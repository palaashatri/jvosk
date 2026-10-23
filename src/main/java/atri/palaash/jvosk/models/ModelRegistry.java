package atri.palaash.jvosk.models;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Registry for the Vosk model catalogue.
 *
 * <p>The official machine-readable catalogue at
 * {@code https://alphacephei.com/vosk/models/model-list.json} is the primary
 * source. If it cannot be fetched or parsed, a snapshot bundled with the
 * application is used so the Model Manager remains useful while offline. The
 * snapshot is generated from that same upstream file.</p>
 *
 * <p>Speaker-identification and text-to-speech archives are filtered out, as
 * are entries the catalogue marks as obsolete: this application can only use
 * current speech-to-text models.</p>
 */
public class ModelRegistry {

    static final String OFFICIAL_CATALOG_URL = "https://alphacephei.com/vosk/models/model-list.json";
    static final String BUNDLED_CATALOG_RESOURCE = "/atri/palaash/jvosk/models/model-list.json";

    private static final long CACHE_TTL_MS = 60L * 60L * 1000L;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_CATALOG_BYTES = 2_000_000;
    private static final Pattern MD5_PATTERN = Pattern.compile("^[0-9a-f]{32}$");
    private static final Set<String> NON_TRANSCRIPTION_TYPES = Set.of(
            "spk", "speaker", "speaker-id", "tts", "punct", "punctuation", "recasepunc");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final CatalogLoader remoteLoader;
    private final CatalogLoader fallbackLoader;

    private List<VoskModel> models = List.of();
    private Map<String, List<VoskModel>> modelsByLanguage = Map.of();
    private long lastFetchTime = 0;
    private boolean usingFallbackSnapshot = false;

    public ModelRegistry() {
        this(ModelRegistry::fetchOfficialCatalog, ModelRegistry::loadBundledCatalog);
    }

    /**
     * Visible for tests so the remote/fallback behaviour can be exercised
     * without making network calls.
     */
    ModelRegistry(CatalogLoader remoteLoader, CatalogLoader fallbackLoader) {
        this.remoteLoader = remoteLoader;
        this.fallbackLoader = fallbackLoader;
    }

    /**
     * Fetch models from the official catalogue, falling back to the bundled
     * snapshot. Results are cached for one hour unless {@code forceRefresh} is
     * set.
     *
     * <p>This method performs network I/O and must not be called on the Swing
     * event dispatch thread.</p>
     *
     * @param forceRefresh if true, fetch even if a recent result is cached
     * @return list of all available transcription models
     */
    public synchronized List<VoskModel> fetchModels(boolean forceRefresh) throws IOException {
        long now = System.currentTimeMillis();

        if (!forceRefresh && !models.isEmpty() && (now - lastFetchTime) < CACHE_TTL_MS) {
            return new ArrayList<>(models);
        }

        List<VoskModel> fetched = null;
        Throwable remoteFailure = null;

        try {
            fetched = remoteLoader.load();
        } catch (Exception e) {
            remoteFailure = e;
        }

        if (fetched == null || fetched.isEmpty()) {
            if (remoteFailure != null) {
                System.err.println("Model catalogue download failed (" + remoteFailure.getMessage()
                        + "); using the bundled snapshot.");
            } else {
                System.err.println("Model catalogue was empty; using the bundled snapshot.");
            }

            try {
                fetched = fallbackLoader.load();
            } catch (Exception fallbackFailure) {
                IOException error = new IOException("Failed to load the model catalogue or its bundled snapshot");
                if (remoteFailure != null) {
                    error.addSuppressed(remoteFailure);
                }
                error.addSuppressed(fallbackFailure);
                throw error;
            }
            usingFallbackSnapshot = true;
        } else {
            usingFallbackSnapshot = false;
        }

        if (fetched == null || fetched.isEmpty()) {
            throw new IOException("Model catalogue is empty, including the bundled snapshot");
        }

        rebuildCache(fetched);
        lastFetchTime = now;
        return new ArrayList<>(models);
    }

    /**
     * Whether the most recent fetch used the bundled snapshot after a failed
     * remote fetch.
     */
    public synchronized boolean isUsingFallbackSnapshot() {
        return usingFallbackSnapshot;
    }

    private void rebuildCache(List<VoskModel> fetched) {
        List<VoskModel> sorted = new ArrayList<>(fetched);
        Collections.sort(sorted);
        models = List.copyOf(sorted);

        Map<String, List<VoskModel>> grouped = new LinkedHashMap<>();
        for (VoskModel model : sorted) {
            grouped.computeIfAbsent(model.getLanguage(), k -> new ArrayList<>()).add(model);
        }
        Map<String, List<VoskModel>> immutableGrouped = new LinkedHashMap<>();
        grouped.forEach((language, list) -> immutableGrouped.put(language, List.copyOf(list)));
        modelsByLanguage = Collections.unmodifiableMap(immutableGrouped);
    }

    /**
     * Get models filtered by language.
     */
    public synchronized List<VoskModel> getModelsByLanguage(String language) {
        return new ArrayList<>(modelsByLanguage.getOrDefault(language, Collections.emptyList()));
    }

    /**
     * Get all unique languages available.
     */
    public synchronized Set<String> getAvailableLanguages() {
        return new HashSet<>(modelsByLanguage.keySet());
    }

    /**
     * Find a model by name.
     */
    public synchronized Optional<VoskModel> findModelByName(String name) {
        return models.stream()
                .filter(m -> m.getName().equals(name))
                .findFirst();
    }

    private static List<VoskModel> fetchOfficialCatalog() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(OFFICIAL_CATALOG_URL).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; jvosk/1.0)");

        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Catalogue request returned HTTP " + status);
            }

            try (InputStream in = connection.getInputStream()) {
                byte[] bytes = in.readNBytes(MAX_CATALOG_BYTES + 1);
                if (bytes.length > MAX_CATALOG_BYTES) {
                    throw new IOException("Catalogue response is unexpectedly large");
                }
                return parseCatalog(new ByteArrayInputStream(bytes));
            }
        } finally {
            connection.disconnect();
        }
    }

    static List<VoskModel> loadBundledCatalog() throws IOException {
        try (InputStream in = ModelRegistry.class.getResourceAsStream(BUNDLED_CATALOG_RESOURCE)) {
            if (in == null) {
                throw new IOException("Bundled model catalogue not found: " + BUNDLED_CATALOG_RESOURCE);
            }
            return parseCatalog(in);
        }
    }

    /**
     * Parse the upstream JSON format. Package-private for snapshot tests.
     */
    static List<VoskModel> parseCatalog(InputStream in) throws IOException {
        List<CatalogEntry> entries = OBJECT_MAPPER.readValue(
                in, new TypeReference<List<CatalogEntry>>() {});
        if (entries == null) {
            return List.of();
        }

        Map<String, VoskModel> byName = new LinkedHashMap<>();
        for (CatalogEntry entry : entries) {
            VoskModel model = toModel(entry);
            if (model != null) {
                byName.putIfAbsent(model.getName(), model);
            }
        }

        List<VoskModel> result = new ArrayList<>(byName.values());
        Collections.sort(result);
        return result;
    }

    private static VoskModel toModel(CatalogEntry entry) {
        if (entry == null || entry.name == null) {
            return null;
        }

        String name = entry.name.trim();
        if (!name.startsWith("vosk-") || !VoskModel.isSafeModelName(name)) {
            System.err.println("Skipping catalogue entry with invalid model name: " + entry.name);
            return null;
        }

        if (isObsolete(entry.obsolete)) {
            return null;
        }

        String typeKey = entry.type == null ? "" : entry.type.trim().toLowerCase(Locale.ROOT);
        if (NON_TRANSCRIPTION_TYPES.contains(typeKey)) {
            return null;
        }

        if (!isAllowedDownloadUrl(entry.url)) {
            System.err.println("Skipping catalogue entry with invalid download URL: " + name);
            return null;
        }

        long sizeBytes = Math.max(0L, entry.size);
        String sizeText = entry.size_text;
        if (sizeText == null || sizeText.isBlank()) {
            sizeText = formatSize(sizeBytes);
        }

        String language = firstNonBlank(entry.lang_text, entry.lang, "Unknown");
        String checksum = normalizeMd5(entry.md5);
        String description = entry.version == null || entry.version.isBlank()
                ? ""
                : "Version " + entry.version;

        return new VoskModel.Builder()
                .name(name)
                .language(language)
                .size(sizeText)
                .sizeBytes(sizeBytes)
                .description(description)
                .downloadUrl(entry.url.trim())
                .checksumMd5(checksum)
                .type(mapType(typeKey, sizeBytes))
                .build();
    }

    private static boolean isAllowedDownloadUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URL url = new URL(value.trim());
            String host = url.getHost();
            return "https".equalsIgnoreCase(url.getProtocol())
                    && host != null
                    && (host.equalsIgnoreCase("alphacephei.com")
                        || host.toLowerCase(Locale.ROOT).endsWith(".alphacephei.com"));
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isObsolete(String obsolete) {
        return obsolete != null && "true".equalsIgnoreCase(obsolete.trim());
    }

    private static String normalizeMd5(String md5) {
        if (md5 == null) {
            return null;
        }
        String normalized = md5.trim().toLowerCase(Locale.ROOT);
        return MD5_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private static VoskModel.ModelType mapType(String typeKey, long sizeBytes) {
        return switch (typeKey) {
            case "small" -> VoskModel.ModelType.SMALL;
            case "big", "big-lgraph", "lgraph" -> VoskModel.ModelType.BIG;
            case "spk", "speaker", "speaker-id" -> VoskModel.ModelType.SPEAKER_ID;
            case "punct", "punctuation", "recasepunc" -> VoskModel.ModelType.PUNCTUATION;
            default -> sizeBytes > 500_000_000L
                    ? VoskModel.ModelType.BIG
                    : VoskModel.ModelType.SMALL;
        };
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return fallback;
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) {
            return "Unknown";
        }
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1fGiB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1fMiB", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.1fKiB", bytes / 1024.0);
    }

    /**
     * Loader abstraction so fallback handling can be tested deterministically.
     */
    @FunctionalInterface
    interface CatalogLoader {
        List<VoskModel> load() throws IOException;
    }

    /**
     * Mirrors the fields of the upstream {@code model-list.json} file.
     */
    private static final class CatalogEntry {
        public String lang;
        public String lang_text;
        public String md5;
        public String name;
        public String obsolete;
        public long size;
        public String size_text;
        public String type;
        public String url;
        public String version;
    }
}
