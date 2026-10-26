package atri.palaash.jvosk.models;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final String INSTALL_METADATA_FILE = ".jvosk-install.json";
    private static final int INSTALL_METADATA_SCHEMA = 1;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final Pattern CONTENT_RANGE_PATTERN = Pattern.compile("^bytes (\\d+)-(\\d+)/(\\d+|\\*)$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
                              VoskModel model = readInstalledModel(modelPath, modelName);

                              installedModels.put(modelName, model);
                         }
                     });
            }

        } catch (IOException | UncheckedIOException e) {
            System.err.println("Failed to scan models directory: " + e.getMessage());
        }
    }

    /**
     * Find installed models whose persisted catalogue metadata differs from
     * the current catalogue. Legacy installations without metadata are left
     * alone because their original archive cannot be identified reliably.
     */
    public Map<String, VoskModel> checkForUpdates() throws IOException {
        List<VoskModel> availableModels = registry.fetchModels(true);
        Map<String, VoskModel> updates = new HashMap<>();

        for (VoskModel installed : installedModels.values()) {
            VoskModel available = availableModels.stream()
                    .filter(model -> model.getName().equals(installed.getName()))
                    .findFirst()
                    .orElse(null);
            if (available != null && hasInstallMetadata(installed)
                    && catalogueMetadataDiffers(installed, available)) {
                updates.put(available.getName(), available.withInstalled(installed.getInstalledVersion()));
            }
        }

        return updates;
    }

    private static boolean hasInstallMetadata(VoskModel installed) {
        return installed.getCatalogueVersion() != null
                || installed.getChecksumMd5() != null
                || installed.getSizeBytes() > 0;
    }

    private static boolean catalogueMetadataDiffers(VoskModel installed, VoskModel available) {
        String installedVersion = installed.getCatalogueVersion();
        String availableVersion = available.getCatalogueVersion();
        if (isKnown(installedVersion) && isKnown(availableVersion)
                && !installedVersion.equals(availableVersion)) {
            return true;
        }

        String installedChecksum = installed.getChecksumMd5();
        String availableChecksum = available.getChecksumMd5();
        if (isKnown(installedChecksum) && isKnown(availableChecksum)
                && !installedChecksum.equalsIgnoreCase(availableChecksum)) {
            return true;
        }

        return installed.getSizeBytes() > 0 && available.getSizeBytes() > 0
                && installed.getSizeBytes() != available.getSizeBytes();
    }

    private static boolean isKnown(String value) {
        return value != null && !value.isBlank();
    }

    private VoskModel readInstalledModel(Path modelPath, String modelName) {
        InstallMetadata metadata = readInstallMetadata(modelPath, modelName);
        long sizeBytes = metadata == null ? 0 : metadata.archiveSizeBytes;
        String version = metadata == null ? null : metadata.catalogueVersion;
        VoskModel.ModelType type = sizeBytes > 500_000_000L
                ? VoskModel.ModelType.BIG
                : VoskModel.ModelType.SMALL;

        VoskModel.Builder builder = new VoskModel.Builder()
                .name(modelName)
                .language(extractLanguageFromName(modelName))
                .size(formatSize(sizeBytes))
                .sizeBytes(sizeBytes)
                .downloadUrl(INSTALLED_MODEL_URL)
                .type(type)
                .isInstalled(true)
                .installedVersion(isKnown(version) ? version : modelName)
                .catalogueVersion(version);
        if (metadata != null) {
            builder.checksumMd5(metadata.checksumMd5);
        }
        return builder.build();
    }

    private InstallMetadata readInstallMetadata(Path modelPath, String expectedName) {
        Path metadataPath = modelPath.resolve(INSTALL_METADATA_FILE);
        if (!Files.isRegularFile(metadataPath)) {
            return null;
        }

        try {
            InstallMetadata metadata = OBJECT_MAPPER.readValue(metadataPath.toFile(), InstallMetadata.class);
            if (metadata.schemaVersion != INSTALL_METADATA_SCHEMA
                    || !expectedName.equals(metadata.modelName)
                    || metadata.archiveSizeBytes < 0) {
                return null;
            }
            return metadata;
        } catch (IOException | RuntimeException e) {
            System.err.println("Ignoring invalid install metadata for " + expectedName + ": " + e.getMessage());
            return null;
        }
    }

    private void writeInstallMetadata(Path modelPath, VoskModel model) throws IOException {
        InstallMetadata metadata = new InstallMetadata();
        metadata.schemaVersion = INSTALL_METADATA_SCHEMA;
        metadata.modelName = model.getName();
        metadata.catalogueVersion = model.getCatalogueVersion();
        metadata.archiveSizeBytes = model.getSizeBytes();
        metadata.checksumMd5 = model.getChecksumMd5();

        Path metadataPath = modelPath.resolve(INSTALL_METADATA_FILE);
        Path temporaryPath = modelPath.resolve(INSTALL_METADATA_FILE + ".part");
        try {
            OBJECT_MAPPER.writeValue(temporaryPath.toFile(), metadata);
            moveIntoPlace(temporaryPath, metadataPath);
        } finally {
            deleteQuietly(temporaryPath);
        }
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) {
            return "Unknown";
        }
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(java.util.Locale.ROOT, "%.1fGiB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
        if (bytes >= 1024L * 1024L) {
            return String.format(java.util.Locale.ROOT, "%.1fMiB", bytes / (1024.0 * 1024.0));
        }
        return String.format(java.util.Locale.ROOT, "%.1fKiB", bytes / 1024.0);
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
     * Return legacy installations that lack metadata needed for reliable
     * catalogue-update comparisons.
     */
    public List<VoskModel> getInstalledModelsWithoutUpdateMetadata() {
        return getInstalledModels().stream().filter(model -> !hasInstallMetadata(model)).toList();
    }

    /**
     * Write {@code .jvosk-install.json} for valid installed models that predate
     * persistence, copying version, size and checksum from a matching catalogue
     * entry. Staging/backup directories, unsafe names and invalid Vosk trees are
     * ignored because {@link #scanInstalledModels()} never lists them. Existing
     * metadata is left unchanged. Returns the number of files written.
     */
    public int migrateLegacyInstalls() throws IOException {
        Map<String, VoskModel> catalogueByName = new HashMap<>();
        for (VoskModel model : registry.fetchModels(false)) {
            catalogueByName.put(model.getName(), model);
        }

        scanInstalledModels();
        int written = 0;
        for (VoskModel installed : getInstalledModelsWithoutUpdateMetadata()) {
            VoskModel catalogueEntry = catalogueByName.get(installed.getName());
            if (catalogueEntry == null) {
                continue;
            }
            writeInstallMetadata(modelsDirectory.resolve(installed.getName()), catalogueEntry);
            written++;
        }
        if (written > 0) {
            scanInstalledModels();
        }
        return written;
    }

    /**
     * Return the names and current byte counts of resumable partial downloads.
     */
    public Map<String, Long> getPartialDownloads() throws IOException {
        Map<String, Long> partials = new java.util.TreeMap<>();
        String prefix = ".jvosk-download-";
        String suffix = ".zip.part";
        try (var paths = Files.list(modelsDirectory)) {
            for (Path path : paths.toList()) {
                String fileName = path.getFileName().toString();
                if (!fileName.startsWith(prefix) || !fileName.endsWith(suffix)
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                String name = fileName.substring(prefix.length(), fileName.length() - suffix.length());
                long size = Files.size(path);
                if (size > 0 && VoskModel.isSafeModelName(name)) {
                    partials.put(name, size);
                }
            }
        }
        return java.util.Collections.unmodifiableMap(partials);
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
            tempZip = modelsDirectory.resolve(".jvosk-download-" + modelName + ".zip.part");
            if (Files.isSymbolicLink(tempZip)) {
                Files.delete(tempZip);
            }
            if (!Files.exists(tempZip, LinkOption.NOFOLLOW_LINKS)) {
                Files.createFile(tempZip);
            }

            // Keep this name stable so interrupted transfers can be resumed.
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

            writeInstallMetadata(tempExtract, model);

            installDirectory(tempExtract, extractPath);
            tempExtract = null;

            installedModels.put(modelName, readInstalledModel(extractPath, modelName));

        } catch (IOException | RuntimeException e) {
            deleteQuietly(zipPath);
            deleteDirectoryQuietly(tempExtract);
            if (tempZip != null) {
                try {
                    if (Files.exists(tempZip) && Files.size(tempZip) == 0) {
                        deleteQuietly(tempZip);
                    }
                } catch (IOException ignored) {
                }
            }
            throw e;
        } finally {
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

        if (isCompletePartial(model, destination)) {
            if (progressCallback != null) {
                progressCallback.accept(100);
            }
            return;
        }

        long resumeOffset = Files.size(destination);
        long catalogueSize = model.getSizeBytes();
        if (catalogueSize > 0 && resumeOffset > catalogueSize) {
            deleteQuietly(destination);
            resumeOffset = 0;
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; jvosk/1.0)");
        connection.setRequestProperty("Accept", "application/zip,application/octet-stream");
        connection.setRequestProperty("Accept-Encoding", "identity");
        if (resumeOffset > 0) {
            connection.setRequestProperty("Range", "bytes=" + resumeOffset + "-");
        }

        try {
            int status = connection.getResponseCode();
            if (status == 416 && resumeOffset > 0) {
                connection.disconnect();
                deleteQuietly(destination);
                downloadFile(model, destination, progressCallback);
                return;
            }
            if (status != HttpURLConnection.HTTP_OK
                    && !(status == HttpURLConnection.HTTP_PARTIAL && resumeOffset > 0)) {
                closeQuietly(connection.getErrorStream());
                throw new IOException("Download failed with HTTP " + status + " for " + model.getName());
            }

            long contentLength = connection.getContentLengthLong();
            boolean append = status == HttpURLConnection.HTTP_PARTIAL;
            long serverTotal = -1;
            if (append) {
                Matcher range = CONTENT_RANGE_PATTERN.matcher(connection.getHeaderField("Content-Range") == null
                        ? ""
                        : connection.getHeaderField("Content-Range"));
                if (!range.matches()) {
                    deleteQuietly(destination);
                    throw new IOException("Server returned an invalid Content-Range for " + model.getName());
                }
                long rangeStart;
                long rangeEnd;
                try {
                    rangeStart = Long.parseLong(range.group(1));
                    rangeEnd = Long.parseLong(range.group(2));
                    serverTotal = "*".equals(range.group(3)) ? -1 : Long.parseLong(range.group(3));
                } catch (NumberFormatException e) {
                    deleteQuietly(destination);
                    throw new IOException("Server returned an invalid Content-Range for " + model.getName(), e);
                }
                if (rangeStart != resumeOffset || rangeEnd < rangeStart
                        || (serverTotal > 0 && rangeEnd >= serverTotal)
                        || (serverTotal > 0 && catalogueSize > 0 && serverTotal != catalogueSize)) {
                    deleteQuietly(destination);
                    throw new IOException("Server returned a mismatched Content-Range for " + model.getName());
                }
            } else {
                resumeOffset = 0; // Server ignored Range; restart cleanly.
                if (contentLength > 0 && catalogueSize > 0 && contentLength != catalogueSize) {
                    deleteQuietly(destination);
                    throw new IOException(String.format(
                            "Server reports %d bytes for %s but the catalogue lists %d bytes",
                            contentLength, model.getName(), catalogueSize));
                }
            }

            // Prefer the catalogue size: it is what the app displays and what
            // the checksum refers to. For a range response, Content-Length is
            // the remaining segment, so use the total from Content-Range.
            long expectedSize = catalogueSize > 0 ? catalogueSize
                    : serverTotal > 0 ? serverTotal
                    : contentLength > 0 ? resumeOffset + contentLength
                    : 0;
            if (append && contentLength > 0 && serverTotal > 0
                    && resumeOffset + contentLength != serverTotal) {
                deleteQuietly(destination);
                throw new IOException("Server returned an incomplete range for " + model.getName());
            }

            long totalBytesRead = resumeOffset;
            int lastProgress = -1;

            try (InputStream rawIn = connection.getInputStream();
                 InputStream in = new BufferedInputStream(rawIn);
                 OutputStream out = Files.newOutputStream(destination,
                         StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                         append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {

                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    if (DownloadManager.getInstance().isCancellationRequested()
                            || Thread.currentThread().isInterrupted()) {
                        throw new IOException("Download cancelled by user");
                    }

                    out.write(buffer, 0, bytesRead);
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
                deleteQuietly(destination);
                throw new IOException("Downloaded model archive is empty");
            }
            if (expectedSize > 0 && actualSize != expectedSize) {
                deleteQuietly(destination);
                throw new IOException(String.format(
                        "Downloaded size mismatch: expected %d bytes but received %d bytes",
                        expectedSize, actualSize));
            }

            String expectedMd5 = model.getChecksumMd5();
            if (expectedMd5 != null) {
                String actualMd5 = computeMd5(destination);
                if (!actualMd5.equalsIgnoreCase(expectedMd5)) {
                    deleteQuietly(destination);
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

    private boolean isCompletePartial(VoskModel model, Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }

        long size = Files.size(path);
        if (size <= 0) {
            return false;
        }

        long expectedSize = model.getSizeBytes();
        if (expectedSize > 0 && size > expectedSize) {
            deleteQuietly(path);
            return false;
        }
        if (expectedSize > 0 && size < expectedSize) {
            return false;
        }
        if (model.getChecksumMd5() != null
                && !model.getChecksumMd5().equalsIgnoreCase(computeMd5(path))) {
            if (expectedSize > 0 && size == expectedSize) {
                deleteQuietly(path);
                return false;
            }
            return false;
        }
        return expectedSize > 0 || model.getChecksumMd5() != null;
    }

    private static String computeMd5(Path path) throws IOException {
        MessageDigest digest = newMd5Digest();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
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

    private static final class InstallMetadata {
        public int schemaVersion;
        public String modelName;
        public String catalogueVersion;
        public long archiveSizeBytes;
        public String checksumMd5;
    }

    public Path getModelsDirectory() {
        return modelsDirectory;
    }
}
