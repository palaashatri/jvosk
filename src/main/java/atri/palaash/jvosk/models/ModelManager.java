package atri.palaash.jvosk.models;

import org.vosk.Model;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages Vosk models including downloading, installation, version checking, and loading.
 */
public class ModelManager {
    
    private final Path modelsDirectory;
    private final ModelRegistry registry;
    private final Map<String, Model> loadedModels;
    private final Map<String, VoskModel> installedModels;
    
    public ModelManager(String modelsPath) {
        this.modelsDirectory = Paths.get(modelsPath);
        this.registry = new ModelRegistry();
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
            
            Files.list(modelsDirectory)
                    .filter(Files::isDirectory)
                    .forEach(modelPath -> {
                        String modelName = modelPath.getFileName().toString();
                        
                        // Verify it's a valid Vosk model (contains required files)
                        if (isValidVoskModel(modelPath)) {
                            VoskModel model = new VoskModel.Builder()
                                    .name(modelName)
                                    .language(extractLanguageFromName(modelName))
                                    .downloadUrl("") // Already installed
                                    .isInstalled(true)
                                    .installedVersion(modelName)
                                    .build();
                            
                            installedModels.put(modelName, model);
                        }
                    });
                    
        } catch (IOException e) {
            System.err.println("Failed to scan models directory: " + e.getMessage());
        }
    }
    
    /**
     * Check if updates are available for installed models.
     */
    public Map<String, VoskModel> checkForUpdates() throws IOException {
        List<VoskModel> availableModels = registry.fetchModels(true);
        Map<String, VoskModel> updates = new HashMap<>();
        
        for (VoskModel installed : installedModels.values()) {
            for (VoskModel available : availableModels) {
                if (available.getName().equals(installed.getName())) {
                    // Simple version comparison - you could make this more sophisticated
                    if (!available.getName().equals(installed.getInstalledVersion())) {
                        updates.put(installed.getName(), available);
                    }
                    break;
                }
            }
        }
        
        return updates;
    }
    
    /**
     * Get all available models from registry (online).
     */
    public List<VoskModel> getAvailableModels() throws IOException {
        List<VoskModel> available = registry.fetchModels(false);
        
        // Mark installed models
        List<VoskModel> result = new ArrayList<>();
        for (VoskModel model : available) {
            if (installedModels.containsKey(model.getName())) {
                VoskModel installed = installedModels.get(model.getName());
                result.add(new VoskModel.Builder()
                        .name(model.getName())
                        .language(model.getLanguage())
                        .size(model.getSize())
                        .accuracy(model.getAccuracy())
                        .description(model.getDescription())
                        .license(model.getLicense())
                        .downloadUrl(model.getDownloadUrl())
                        .type(model.getType())
                        .isInstalled(true)
                        .installedVersion(installed.getInstalledVersion())
                        .build());
            } else {
                result.add(model);
            }
        }
        
        return result;
    }
    
    /**
     * Get all installed models.
     */
    public List<VoskModel> getInstalledModels() {
        return new ArrayList<>(installedModels.values());
    }
    
    /**
     * Download and install a model.
     * @param model the model to download
     * @param progressCallback callback for download progress (0-100)
     */
    public void downloadModel(VoskModel model, Consumer<Integer> progressCallback) throws IOException {
        Path zipPath = modelsDirectory.resolve(model.getName() + ".zip");
        Path extractPath = modelsDirectory.resolve(model.getName());
        
        try {
            // Download
            downloadFile(model.getDownloadUrl(), zipPath, progressCallback);
            
            // Check for cancellation
            if (DownloadManager.getInstance().isCancellationRequested()) {
                throw new IOException("Download cancelled by user");
            }
            
            // Extract
            extractZip(zipPath, extractPath);
            
            // Verify installation
            if (isValidVoskModel(extractPath)) {
                VoskModel installed = new VoskModel.Builder()
                        .name(model.getName())
                        .language(model.getLanguage())
                        .size(model.getSize())
                        .downloadUrl(model.getDownloadUrl())
                        .type(model.getType())
                        .isInstalled(true)
                        .installedVersion(model.getName())
                        .build();
                
                installedModels.put(model.getName(), installed);
            } else {
                throw new IOException("Downloaded model is not valid");
            }
            
        } catch (IOException e) {
            // Clean up on failure
            try {
                Files.deleteIfExists(zipPath);
                deleteDirectory(extractPath);
            } catch (IOException ignored) {}
            throw e;
        } finally {
            // Clean up zip file
            try {
                Files.deleteIfExists(zipPath);
            } catch (IOException ignored) {}
            
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
        // Check if already loaded
        if (loadedModels.containsKey(modelName)) {
            return loadedModels.get(modelName);
        }
        
        Path modelPath = modelsDirectory.resolve(modelName);
        
        if (!Files.exists(modelPath)) {
            throw new IOException("Model not found: " + modelName);
        }
        
        if (!isValidVoskModel(modelPath)) {
            throw new IOException("Invalid Vosk model: " + modelName);
        }
        
        Model model = new Model(modelPath.toString());
        loadedModels.put(modelName, model);
        
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
        // Unload if loaded
        unloadModel(modelName);
        
        Path modelPath = modelsDirectory.resolve(modelName);
        
        if (Files.exists(modelPath)) {
            deleteDirectory(modelPath);
        }
        
        installedModels.remove(modelName);
    }
    
    private void downloadFile(String urlString, Path destination, Consumer<Integer> progressCallback) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; jvosk/1.0)");
        
        long fileSize = connection.getContentLengthLong();
        
        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             FileOutputStream out = new FileOutputStream(destination.toFile())) {
            
            byte[] buffer = new byte[8192];
            long totalBytesRead = 0;
            int bytesRead;
            int lastProgress = 0;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                // Check for cancellation request
                if (DownloadManager.getInstance().isCancellationRequested()) {
                    throw new IOException("Download cancelled by user");
                }
                
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                
                if (fileSize > 0 && progressCallback != null) {
                    int progress = (int) ((totalBytesRead * 100) / fileSize);
                    if (progress != lastProgress) {
                        progressCallback.accept(progress);
                        lastProgress = progress;
                    }
                }
            }
            
            if (progressCallback != null) {
                progressCallback.accept(100);
            }
        }
    }
    
    private void extractZip(Path zipPath, Path destinationDir) throws IOException {
        // Delete destination if it exists
        if (Files.exists(destinationDir)) {
            deleteDirectory(destinationDir);
        }
        
        Files.createDirectories(destinationDir);
        
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = destinationDir.resolve(entry.getName());
                
                // Security check - prevent zip slip vulnerability
                if (!entryPath.normalize().startsWith(destinationDir.normalize())) {
                    throw new IOException("Invalid zip entry: " + entry.getName());
                }
                
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    // Ensure parent directory exists
                    Files.createDirectories(entryPath.getParent());
                    
                    // Extract file
                    try (FileOutputStream fos = new FileOutputStream(entryPath.toFile())) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                
                zis.closeEntry();
            }
        }
        
        // Flatten if model is in a subdirectory
        flattenModelDirectory(destinationDir);
    }
    
    private void flattenModelDirectory(Path modelDir) throws IOException {
        // Check if model files are in a subdirectory
        List<Path> topLevelDirs = Files.list(modelDir)
                .filter(Files::isDirectory)
                .toList();
        
        // If there's exactly one directory and no model files at top level
        if (topLevelDirs.size() == 1 && !hasModelFiles(modelDir)) {
            Path subDir = topLevelDirs.get(0);
            
            // Move all files from subdir to parent
            Files.list(subDir).forEach(file -> {
                try {
                    Files.move(file, modelDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    System.err.println("Failed to move file: " + file);
                }
            });
            
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
    
    private void deleteDirectory(Path directory) throws IOException {
        Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + path);
                    }
                });
    }
    
    public Path getModelsDirectory() {
        return modelsDirectory;
    }
}
