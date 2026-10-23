package atri.palaash.jvosk.models;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a Vosk model with metadata including download information, version, and performance metrics.
 */
public class VoskModel implements Comparable<VoskModel> {

    private static final Pattern SIZE_PATTERN = Pattern.compile(
            "^([0-9]+(?:\\.[0-9]+)?)\\s*([KMGTP]?I?B?)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");
    private static final int MAX_NAME_LENGTH = 200;

    private final String name;
    private final String language;
    private final String size;
    private final long sizeBytes;
    private final String accuracy;
    private final String description;
    private final String license;
    private final String downloadUrl;
    private final String checksumMd5;
    private final String catalogueVersion;
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
        this.checksumMd5 = builder.checksumMd5;
        this.catalogueVersion = builder.catalogueVersion;
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
    public String getChecksumMd5() { return checksumMd5; }
    public String getCatalogueVersion() { return catalogueVersion; }
    public ModelType getType() { return type; }
    public boolean isInstalled() { return isInstalled; }
    public String getInstalledVersion() { return installedVersion; }

    public boolean isBigModel() {
        return type == ModelType.BIG || sizeBytes > 500_000_000; // > 500MB
    }

    public String getDisplayName() {
        return String.format("%s [%s] (%s)", name, language, size);
    }

    /**
     * Return a copy of this model marked as installed under the given version.
     * Installed state is not part of a catalogue entry, so it must not mutate
     * the cached model shared by the UI.
     */
    public VoskModel withInstalled(String version) {
        return new Builder()
                .name(name)
                .language(language)
                .size(size)
                .sizeBytes(sizeBytes)
                .accuracy(accuracy)
                .description(description)
                .license(license)
                .downloadUrl(downloadUrl)
                .checksumMd5(checksumMd5)
                .catalogueVersion(catalogueVersion)
                .type(type)
                .isInstalled(true)
                .installedVersion(version)
                .build();
    }

    /**
     * Model names become directory names under the model root. Reject anything
     * that could escape that root or contain platform-specific separators.
     */
    public static boolean isSafeModelName(String name) {
        return name != null
                && !name.isBlank()
                && name.length() <= MAX_NAME_LENGTH
                && SAFE_NAME_PATTERN.matcher(name).matches()
                && !name.contains("..");
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

    /**
     * Parse human-readable sizes such as {@code 40M}, {@code 1.8G} and
     * {@code 1.3GiB}. Returns zero when the value is absent or unrecognised.
     */
    static long parseSizeToBytes(String sizeStr) {
        if (sizeStr == null || sizeStr.isBlank()) return 0;

        Matcher matcher = SIZE_PATTERN.matcher(sizeStr.trim());
        if (!matcher.matches()) return 0;

        try {
            double value = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2);
            long multiplier = 1;

            if (unit != null && !unit.isBlank()) {
                switch (Character.toUpperCase(unit.charAt(0))) {
                    case 'K' -> multiplier = 1024L;
                    case 'M' -> multiplier = 1024L * 1024L;
                    case 'G' -> multiplier = 1024L * 1024L * 1024L;
                    case 'T' -> multiplier = 1024L * 1024L * 1024L * 1024L;
                    case 'P' -> multiplier = 1024L * 1024L * 1024L * 1024L * 1024L;
                    default -> multiplier = 1;
                }
            }

            return Math.round(value * multiplier);
        } catch (NumberFormatException e) {
            return 0;
        }
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
        private String checksumMd5 = null;
        private String catalogueVersion;
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
            if (this.sizeBytes <= 0) {
                this.sizeBytes = parseSizeToBytes(size);
            }
            return this;
        }

        /**
         * Set the exact archive size from a machine-readable catalogue. This
         * overrides the rounded value derived from the display string.
         */
        public Builder sizeBytes(long sizeBytes) {
            if (sizeBytes < 0) {
                throw new IllegalArgumentException("Model size cannot be negative");
            }
            this.sizeBytes = sizeBytes;
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

        public Builder checksumMd5(String checksumMd5) {
            this.checksumMd5 = checksumMd5;
            return this;
        }

        /**
         * Set the version string supplied by the Vosk model catalogue.
         */
        public Builder catalogueVersion(String catalogueVersion) {
            this.catalogueVersion = catalogueVersion;
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
            if (!isSafeModelName(name)) {
                throw new IllegalArgumentException("Unsafe model name: " + name);
            }
            if (type == null) {
                throw new IllegalArgumentException("Model type is required");
            }

            // Store checksums in a canonical form so comparisons are simple.
            String normalizedChecksum = checksumMd5 == null ? null : checksumMd5.trim();
            this.checksumMd5 = normalizedChecksum == null || normalizedChecksum.isEmpty()
                    ? null
                    : normalizedChecksum.toLowerCase(Locale.ROOT);

            return new VoskModel(this);
        }
    }
}
