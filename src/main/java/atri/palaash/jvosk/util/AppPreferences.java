package atri.palaash.jvosk.util;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

public class AppPreferences {
    
    private static final Preferences prefs = Preferences.userNodeForPackage(AppPreferences.class);
    
    private static final String LAST_SAVE_DIR = "lastSaveDir";
    private static final String LAST_OPEN_DIR = "lastOpenDir";
    private static final String DARK_MODE = "darkMode";
    private static final String SHOW_TIMESTAMPS = "showTimestamps";
    private static final String RECENT_FILES = "recentFiles";
    private static final String SELECTED_MODEL = "selectedModel";
    private static final String FONT_SIZE = "fontSize";
    private static final String ENHANCE_SPEECH = "enhanceSpeech";
    private static final int MAX_RECENT_FILES = 10;
    
    public static String getLastSaveDir() {
        return prefs.get(LAST_SAVE_DIR, System.getProperty("user.home"));
    }
    
    public static void setLastSaveDir(String path) {
        prefs.put(LAST_SAVE_DIR, path);
    }
    
    public static String getLastOpenDir() {
        return prefs.get(LAST_OPEN_DIR, System.getProperty("user.home"));
    }
    
    public static void setLastOpenDir(String path) {
        prefs.put(LAST_OPEN_DIR, path);
    }
    
    public static boolean isDarkMode() {
        return prefs.getBoolean(DARK_MODE, false);
    }
    
    public static void setDarkMode(boolean darkMode) {
        prefs.putBoolean(DARK_MODE, darkMode);
    }
    
    public static boolean isShowTimestamps() {
        return prefs.getBoolean(SHOW_TIMESTAMPS, false);
    }
    
    public static void setShowTimestamps(boolean show) {
        prefs.putBoolean(SHOW_TIMESTAMPS, show);
    }

    public static boolean isEnhanceSpeech() {
        return prefs.getBoolean(ENHANCE_SPEECH, false);
    }

    public static void setEnhanceSpeech(boolean enhance) {
        prefs.putBoolean(ENHANCE_SPEECH, enhance);
    }
    
    public static String getSelectedModel() {
        return prefs.get(SELECTED_MODEL, "models/vosk-model-small-en-us-0.15");
    }
    
    public static void setSelectedModel(String modelPath) {
        prefs.put(SELECTED_MODEL, modelPath);
    }

    /**
     * Point the saved model path at its new directory after a verified
     * catalogue rename. Only paths directly inside {@code modelsDirectory} are
     * remapped, so unrelated saved paths are never rewritten.
     *
     * @param modelsDirectory root that contains installed model directories
     * @param renames old directory name to new directory name
     */
    public static void remapSelectedModel(Path modelsDirectory, Map<String, String> renames) {
        if (modelsDirectory == null || renames == null || renames.isEmpty()) {
            return;
        }
        Path selected;
        try {
            selected = Paths.get(getSelectedModel()).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return;
        }
        for (Map.Entry<String, String> rename : renames.entrySet()) {
            Path oldPath = modelsDirectory.resolve(rename.getKey()).toAbsolutePath().normalize();
            if (selected.equals(oldPath)) {
                Path newPath = modelsDirectory.resolve(rename.getValue())
                        .toAbsolutePath().normalize();
                setSelectedModel(newPath.toString());
                return;
            }
        }
    }
    
    public static int getFontSize() {
        return prefs.getInt(FONT_SIZE, 12);
    }
    
    public static void setFontSize(int size) {
        prefs.putInt(FONT_SIZE, size);
    }
    
    public static List<String> getRecentFiles() {
        String recent = prefs.get(RECENT_FILES, "");
        List<String> files = new ArrayList<>();
        if (!recent.isEmpty()) {
            for (String path : recent.split("\\|")) {
                if (new File(path).exists()) {
                    files.add(path);
                }
            }
        }
        return files;
    }
    
    public static void addRecentFile(String path) {
        List<String> files = getRecentFiles();
        files.remove(path); // Remove if already exists
        files.add(0, path); // Add to front
        
        // Keep only MAX_RECENT_FILES
        if (files.size() > MAX_RECENT_FILES) {
            files = files.subList(0, MAX_RECENT_FILES);
        }
        
        prefs.put(RECENT_FILES, String.join("|", files));
    }
}
