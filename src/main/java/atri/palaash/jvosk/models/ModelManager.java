package atri.palaash.jvosk.models;

import org.vosk.Model;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages Vosk models including downloading, installation, version checking, and loading.
 */
public class ModelManager {

    // Sentinel value for installed models that don't have a remote download URL
    private static final String INSTALLED_MODEL_URL = "installed://local";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final Path modelsDirectory;
    private final ModelRegistry registry;
    private final Map<String, Model> loadedModels;
    private final Map<String, VoskModel> installedModels;

    public ModelManager(String modelsPath) {
        this(modelsPath, new ModelRegistry());
    }

    /**
     * Visible for tests so catalogue results can be supplied without network
     * access.
     */
    ModelManager(String modelsPath, ModelRegistry registry) {
        this.modelsDirectory = Paths.get(modelsPath);
        this.registry = Objects.requireNonNull(registry, "registry");
        this.loadedModels = new ConcurrentHashMap<>();
        this.installedModels = new ConcurrentHashMap<>();

        // Ensure models directory exists
        try {
            Files.createDirectories(modelsDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create models directory: " + modelsDirectory, e);
        }

        // Scan for installed models
        scanInstalledModels();
    }

    /**
     * Scan the models directory for installed models.
     */
    public void scanInstalledModels() {
        installedModels.clear();

        try {
            if (!Files.exists(modelsDirectory)) {
                return;
            }

            try (var paths = Files.list(modelsDirectory)) {
                paths.filter(Files::isDirectory)
                     .forEach(modelPath -> {
                         String modelName = modelPath.getFileName().toString();

                         // Skip staging/backup directories and any directory
                         // whose name could escape the model root.
                         if (!VoskModel.isSafeModelName(modelName)) {
                             return;
                         }

                         // Verify it's a valid Vosk model (contains required files)
                         if (isValidVoskModel(modelPath)) {
                             VoskModel model = new VoskModel.Builder()
                                     .name(modelName)
                                     .language(extractLanguageFromName(modelName))
                                     .downloadUrl(INSTALLED_MODEL_URL) // Already installed
                                     .isInstalled(true)
                                     .installedVersion(modelName)
                                     .build();

                             installedModels.put(modelName, model);
                         }
                     });
            }

        } catch (IOException | UncheckedIOException e) {
            System.err.println("Failed to scan models directory: " + e.getMessage());
        }
    }

    /**
     * Check if updates are available for installed models.
     * Since Vosk models don't have explicit version numbers in their names,
     * this method performs a simple name-based check. If the model name changes
     * on the server, it will be detected as an update.
     */
    public Map<String, VoskModel> checkForUpdates() throws IOException {
        List<VoskModel> availableModels = registry.fetchModels(true);
        Map<String, VoskModel> updates = new HashMap<>();

        for (VoskModel installed : installedModels.values()) {
            for (VoskModel available : availableModels) {
                if (available.getName().equals(installed.getName())) {
                    // Since Vosk models don't have version numbers, we can't reliably
                    // detect updates. This is a limitation of the current implementation.
                    // For now, we skip update detection.
                    // In the future, this could be enhanced with checksums or size comparison.
                    break;
                }
            }
        }

        return updates;
    }

    /**
     * Get all available models from registry (online, or the bundled snapshot
     * when the network is unavailable).
     */
    public List<VoskModel> getAvailableModels() throws IOException {
        return getAvailableModels(false);
    }

    /**
     * Get all available models, optionally bypassing the hourly catalogue cache.
     */
    public List<VoskModel> getAvailableModels(boolean forceRefresh) throws IOException {
        List<VoskModel> available = registry.fetchModels(forceRefresh);

        // Mark installed models and keep locally installed models that are no
        // longer in the catalogue (for example, models the catalogue now marks
        // obsolete) so they can still be selected or deleted.
        List<VoskModel> result = new ArrayList<>();
        for (VoskModel model : available) {
            VoskModel installed = installedModels.get(model.getName());
            result.add(installed == null
                    ? model
                    : model.withInstalled(installed.getInstalledVersion()));
        }

        for (VoskModel installed : getInstalledModels()) {
            boolean inCatalogue = available.stream()
                    .anyMatch(model -> model.getName().equals(installed.getName()));
            if (!inCatalogue) {
                result.add(installed);
            }
        }

        return result;
    }

    /**
     * Whether the latest catalogue request used the bundled snapshot.
     */
    public boolean isUsingFallbackCatalogue() {
        return registry.isUsingFallbackSnapshot();
    }

    /**
     * Get all installed models.
     */
    public List<VoskModel> getInstalledModels() {
        return installedModels.values().stream().sorted().toList();
    }

    /**
     * Download and install a model.
     *
     * @param model the model to download
     * @param progressCallback callback for download progress (0-100)
     */
    public void downloadModel(VoskModel model, Consumer<Integer> progressCallback) throws IOException {
        Objects.requireNonNull(model, "model");
        String modelName = requireSafeModelName(model.getName());

        Path zipPath = modelsDirectory.resolve(modelName + ".zip");
        Path extractPath = modelsDirectory.resolve(modelName);
        Path tempZip = null;
        Path tempExtract = null;

        try {
            tempZip = Files.createTempFile(modelsDirectory, ".jvosk-download-", ".zip.part");

            // Download to a temporary file first so a failed transfer never
            // looks like an installed archive.
            downloadFile(model, tempZip, progressCallback);

            if (DownloadManager.getInstance().isCancellationRequested()) {
                throw new IOException("Download cancelled by user");
            }

            moveIntoPlace(tempZip, zipPath);
            tempZip = null;

            // Extract into a staging directory and validate before replacing an
            // existing installation.
            tempExtract = Files.createTempDirectory(modelsDirectory, ".jvosk-extract-");
            extractZip(zipPath, tempExtract);

            if (!isValidVoskModel(tempExtract)) {
                throw new IOException("Downloaded model is not valid");
            }

            installDirectory(tempExtract, extractPath);
            tempExtract = null;

            installedModels.put(modelName, model.withInstalled(modelName));

        } catch (IOException | RuntimeException e) {
            deleteQuietly(zipPath);
            deleteDirectoryQuietly(tempExtract);
            throw e;
        } finally {
            deleteQuietly(tempZip);
            deleteQuietly(zipPath);
            deleteDirectoryQuietly(tempExtract);

            // Clear active download
            DownloadManager.getInstance().clearActiveDownload();
        }
    }

    /**
     * Check if a download is currently active.
     */
    public boolean isDownloadInProgress() {
        return DownloadManager.getInstance().hasActiveDownload();
    }

    /**
     * Cancel the current download.
     */
    public void cancelDownload() {
        DownloadManager.getInstance().cancelCurrentDownload();
    }

    /**
     * Load a model into memory for use.
     */
    public Model loadModel(String modelName) throws IOException {
        String safeName = requireSafeModelName(modelName);

        // Check if already loaded
        if (loadedModels.containsKey(safeName)) {
            return loadedModels.get(safeName);
        }

        Path modelPath = modelsDirectory.resolve(safeName);

        if (!Files.exists(modelPath)) {
            throw new IOException("Model not found: " + safeName);
        }

        if (!isValidVoskModel(modelPath)) {
            throw new IOException("Invalid Vosk model: " + safeName);
        }

        Model model = new Model(modelPath.toString());
        loadedModels.put(safeName, model);

        return model;
    }

    /**
     * Unload a model from memory.
     */
    public void unloadModel(String modelName) {
        Model model = loadedModels.remove(modelName);
        if (model != null) {
            model.close();
        }
    }

    /**
     * Delete an installed model.
     */
    public void deleteModel(String modelName) throws IOException {
        String safeName = requireSafeModelName(modelName);

        // Unload if loaded
        unloadModel(safeName);

        Path modelPath = modelsDirectory.resolve(safeName);

        if (Files.exists(modelPath)) {
            deleteDirectory(modelPath);
        }

        installedModels.remove(safeName);
    }

    private void downloadFile(VoskModel model, Path destination, Consumer<Integer> progressCallback) throws IOException {
        URL url;
        try {
            url = URI.create(model.getDownloadUrl()).toURL();
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid model download URL: " + model.getDownloadUrl(), e);
        }

        if (!"http".equalsIgnoreCase(url.getProtocol())
                && !"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("Unsupported model download URL scheme: " + url.getProtocol());
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; jvosk/1.0)");
        connection.setRequestProperty("Accept", "application/zip,application/octet-stream");

        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                closeQuietly(connection.getErrorStream());
                throw new IOException("Download failed with HTTP " + status + " for " + model.getName());
            }

            long contentLength = connection.getContentLengthLong();
            long catalogueSize = model.getSizeBytes();

            if (contentLength > 0 && catalogueSize > 0 && contentLength != catalogueSize) {
                throw new IOException(String.format(
                        "Server reports %d bytes for %s but the catalogue lists %d bytes",
                        contentLength, model.getName(), catalogueSize));
            }

            // Prefer the catalogue size: it is what the app displays and what
            // the checksum refers to. Fall back to Content-Length when the
            // catalogue has no exact size.
            long expectedSize = catalogueSize > 0 ? catalogueSize : contentLength;

            MessageDigest digest = newMd5Digest();
            long totalBytesRead = 0;
            int lastProgress = -1;

            try (InputStream rawIn = connection.getInputStream();
                 InputStream in = new BufferedInputStream(rawIn);
                 OutputStream out = Files.newOutputStream(destination)) {

                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    if (DownloadManager.getInstance().isCancellationRequested()
                            || Thread.currentThread().isInterrupted()) {
                        throw new IOException("Download cancelled by user");
                    }

                    out.write(buffer, 0, bytesRead);
                    digest.update(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    if (expectedSize > 0 && progressCallback != null) {
                        int progress = (int) Math.min(100, (totalBytesRead * 100) / expectedSize);
                        if (progress != lastProgress) {
                            progressCallback.accept(progress);
                            lastProgress = progress;
                        }
                    }
                }
            }

            long actualSize = Files.size(destination);
            if (actualSize == 0) {
                throw new IOException("Downloaded model archive is empty");
            }
            if (expectedSize > 0 && actualSize != expectedSize) {
                throw new IOException(String.format(
                        "Downloaded size mismatch: expected %d bytes but received %d bytes",
                        expectedSize, actualSize));
            }

            String expectedMd5 = model.getChecksumMd5();
            if (expectedMd5 != null) {
                String actualMd5 = HexFormat.of().formatHex(digest.digest());
                if (!actualMd5.equalsIgnoreCase(expectedMd5)) {
                    throw new IOException("Checksum verification failed for " + model.getName()
                            + ": expected " + expectedMd5 + " but got " + actualMd5);
                }
            }

            if (progressCallback != null) {
                progressCallback.accept(100);
            }

        } finally {
            connection.disconnect();
        }
    }

    private static MessageDigest newMd5Digest() throws IOException {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 is not available on this Java runtime", e);
        }
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Replace an existing model directory only after the new one has been
     * extracted and validated.
     */
    private void installDirectory(Path stagedDirectory, Path targetDirectory) throws IOException {
        Path backup = null;

        if (Files.exists(targetDirectory)) {
            backup = modelsDirectory.resolve(".jvosk-backup-" + UUID.randomUUID());
            Files.move(targetDirectory, backup);
        }

        try {
            Files.move(stagedDirectory, targetDirectory);
        } catch (IOException e) {
            if (backup != null && !Files.exists(targetDirectory)) {
                try {
                    Files.move(backup, targetDirectory);
                } catch (IOException restoreFailure) {
                    e.addSuppressed(restoreFailure);
                }
            }
            throw e;
        }

        if (backup != null) {
            deleteDirectoryQuietly(backup);
        }
    }

    /**
     * Extract an archive into {@code destinationDir}, rejecting entries that
     * would escape it (zip slip). Package-private for extraction tests.
     */
    void extractZip(Path zipPath, Path destinationDir) throws IOException {
        Path root = destinationDir.toAbsolutePath().normalize();
        Files.createDirectories(root);

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (DownloadManager.getInstance().isCancellationRequested()) {
                    throw new IOException("Download cancelled by user");
                }

                String entryName = entry.getName();
                if (entryName == null || entryName.isBlank()) {
                    throw new IOException("Invalid empty zip entry");
                }

                // Treat both separators as path separators, then normalise. A
                // crafted '../' or absolute entry must never leave the root.
                Path entryPath;
                try {
                    entryPath = root.resolve(entryName.replace('\\', '/')).normalize();
                } catch (InvalidPathException e) {
                    throw new IOException("Invalid zip entry: " + entryName, e);
                }
                if (!entryPath.startsWith(root)) {
                    throw new IOException("Invalid zip entry: " + entryName);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Path parent = entryPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }

                    try (OutputStream out = Files.newOutputStream(entryPath)) {
                        zis.transferTo(out);
                    }
                }

                zis.closeEntry();
            }
        }

        // Flatten if model is in a subdirectory
        flattenModelDirectory(root);
    }

    private void flattenModelDirectory(Path modelDir) throws IOException {
        // Check if model files are in a subdirectory
        List<Path> topLevelDirs;
        try (var paths = Files.list(modelDir)) {
            topLevelDirs = paths.filter(Files::isDirectory)
                    .toList();
        }

        // If there's exactly one directory and no model files at top level
        if (topLevelDirs.size() == 1 && !hasModelFiles(modelDir)) {
            Path subDir = topLevelDirs.get(0);
            List<Path> entries;
            try (var files = Files.list(subDir)) {
                entries = files.toList();
            }

            // Move all files from subdir to parent
            for (Path file : entries) {
                Files.move(file, modelDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }

            // Delete the now-empty subdirectory
            Files.deleteIfExists(subDir);
        }
    }

    private boolean hasModelFiles(Path dir) {
        try {
            return Files.exists(dir.resolve("am/final.mdl")) ||
                   Files.exists(dir.resolve("conf/model.conf"));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidVoskModel(Path modelPath) {
        // Check for essential Vosk model files
        Path amDir = modelPath.resolve("am");
        Path confDir = modelPath.resolve("conf");
        Path graphDir = modelPath.resolve("graph");

        return Files.exists(amDir.resolve("final.mdl")) &&
               Files.exists(confDir.resolve("mfcc.conf")) &&
               (Files.exists(graphDir.resolve("HCLG.fst")) ||
                Files.exists(graphDir.resolve("HCLr.fst")));
    }

    private String extractLanguageFromName(String modelName) {
        // Extract language code from model name (e.g., vosk-model-small-en-us-0.15 -> English)
        if (modelName.contains("-en-")) return "English";
        if (modelName.contains("-ru-")) return "Russian";
        if (modelName.contains("-fr-")) return "French";
        if (modelName.contains("-de-")) return "German";
        if (modelName.contains("-es-")) return "Spanish";
        if (modelName.contains("-pt-")) return "Portuguese";
        if (modelName.contains("-cn-")) return "Chinese";
        if (modelName.contains("-ja-")) return "Japanese";
        if (modelName.contains("-it-")) return "Italian";
        if (modelName.contains("-nl-")) return "Dutch";
        if (modelName.contains("-ar-")) return "Arabic";
        if (modelName.contains("-fa-")) return "Farsi";
        if (modelName.contains("-hi-")) return "Hindi";
        if (modelName.contains("-uk-")) return "Ukrainian";
        if (modelName.contains("-tr-")) return "Turkish";
        if (modelName.contains("-vn-")) return "Vietnamese";
        if (modelName.contains("-ko-")) return "Korean";

        return "Unknown";
    }

    private static String requireSafeModelName(String modelName) throws IOException {
        if (!VoskModel.isSafeModelName(modelName)) {
            throw new IOException("Invalid model name: " + modelName);
        }
        return modelName;
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        List<Path> paths;
        try (var stream = Files.walk(directory)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }

        IOException failure = null;
        for (Path path : paths) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try {
            deleteDirectory(directory);
        } catch (IOException e) {
            System.err.println("Failed to delete " + directory + ": " + e.getMessage());
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            System.err.println("Failed to delete " + path + ": " + e.getMessage());
        }
    }

    private static void closeQuietly(InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    public Path getModelsDirectory() {
        return modelsDirectory;
    }
}
