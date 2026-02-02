package atri.palaash.jvosk.models;

import java.util.Objects;

/**
 * Represents a Vosk model with metadata including download information, version, and performance metrics.
 */
public class VoskModel implements Comparable<VoskModel> {
    
    private final String name;
    private final String language;
    private final String size;
    private final long sizeBytes;
    private final String accuracy;
    private final String description;
    private final String license;
    private final String downloadUrl;
    private final ModelType type;
    private final boolean isInstalled;
    private final String installedVersion;
    
    public enum ModelType {
        SMALL,
        BIG,
        PUNCTUATION,
        SPEAKER_ID
    }
    
    private VoskModel(Builder builder) {
        this.name = builder.name;
        this.language = builder.language;
        this.size = builder.size;
        this.sizeBytes = builder.sizeBytes;
        this.accuracy = builder.accuracy;
        this.description = builder.description;
        this.license = builder.license;
        this.downloadUrl = builder.downloadUrl;
        this.type = builder.type;
        this.isInstalled = builder.isInstalled;
        this.installedVersion = builder.installedVersion;
    }
    
    // Getters
    public String getName() { return name; }
    public String getLanguage() { return language; }
    public String getSize() { return size; }
    public long getSizeBytes() { return sizeBytes; }
    public String getAccuracy() { return accuracy; }
    public String getDescription() { return description; }
    public String getLicense() { return license; }
    public String getDownloadUrl() { return downloadUrl; }
    public ModelType getType() { return type; }
    public boolean isInstalled() { return isInstalled; }
    public String getInstalledVersion() { return installedVersion; }
    
    public boolean isBigModel() {
        return type == ModelType.BIG || sizeBytes > 500_000_000; // > 500MB
    }
    
    public String getDisplayName() {
        return String.format("%s [%s] (%s)", name, language, size);
    }
    
    @Override
    public int compareTo(VoskModel other) {
        // Sort by language first, then by type (small before big), then by name
        int langCompare = this.language.compareTo(other.language);
        if (langCompare != 0) return langCompare;
        
        int typeCompare = this.type.compareTo(other.type);
        if (typeCompare != 0) return typeCompare;
        
        return this.name.compareTo(other.name);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VoskModel voskModel = (VoskModel) o;
        return Objects.equals(name, voskModel.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
    
    @Override
    public String toString() {
        return getDisplayName();
    }
    
    public static class Builder {
        private String name;
        private String language = "Unknown";
        private String size = "Unknown";
        private long sizeBytes = 0;
        private String accuracy = "";
        private String description = "";
        private String license = "";
        private String downloadUrl;
        private ModelType type = ModelType.SMALL;
        private boolean isInstalled = false;
        private String installedVersion = null;
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder language(String language) {
            this.language = language;
            return this;
        }
        
        public Builder size(String size) {
            this.size = size;
            this.sizeBytes = parseSizeToBytes(size);
            return this;
        }
        
        public Builder accuracy(String accuracy) {
            this.accuracy = accuracy;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder license(String license) {
            this.license = license;
            return this;
        }
        
        public Builder downloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
            return this;
        }
        
        public Builder type(ModelType type) {
            this.type = type;
            return this;
        }
        
        public Builder isInstalled(boolean isInstalled) {
            this.isInstalled = isInstalled;
            return this;
        }
        
        public Builder installedVersion(String installedVersion) {
            this.installedVersion = installedVersion;
            return this;
        }
        
        public VoskModel build() {
            Objects.requireNonNull(name, "Model name is required");
            Objects.requireNonNull(downloadUrl, "Download URL is required");
            return new VoskModel(this);
        }
        
        private long parseSizeToBytes(String sizeStr) {
            if (sizeStr == null || sizeStr.isEmpty()) return 0;
            
            sizeStr = sizeStr.trim().toUpperCase();
            double value = 0;
            
            try {
                if (sizeStr.endsWith("G") || sizeStr.endsWith("GB")) {
                    value = Double.parseDouble(sizeStr.replaceAll("[^0-9.]", ""));
                    return (long)(value * 1024 * 1024 * 1024);
                } else if (sizeStr.endsWith("M") || sizeStr.endsWith("MB")) {
                    value = Double.parseDouble(sizeStr.replaceAll("[^0-9.]", ""));
                    return (long)(value * 1024 * 1024);
                } else if (sizeStr.endsWith("K") || sizeStr.endsWith("KB")) {
                    value = Double.parseDouble(sizeStr.replaceAll("[^0-9.]", ""));
                    return (long)(value * 1024);
                }
            } catch (NumberFormatException e) {
                // Return 0 if parsing fails
            }
            
            return 0;
        }
    }
}
