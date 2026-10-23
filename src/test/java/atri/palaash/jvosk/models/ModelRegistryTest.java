package atri.palaash.jvosk.models;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRegistryTest {

    private static final String TEST_CATALOG = """
            [
              {
                "lang": "en-us",
                "lang_text": "US English",
                "md5": "09ab50ccd62b674cbaa231b825f9c1cb",
                "name": "vosk-model-small-en-us-0.15",
                "obsolete": "false",
                "size": 41205931,
                "size_text": "39.3MiB",
                "type": "small",
                "url": "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
                "version": "0.15"
              },
              {
                "lang": "en-us",
                "lang_text": "US English",
                "md5": "5eb44943eb590cf1c7cec5024b586699",
                "name": "vosk-model-small-en-us-0.3",
                "obsolete": "true",
                "size": 37279669,
                "size_text": "35.6MiB",
                "type": "small",
                "url": "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.3.zip",
                "version": "0.3"
              },
              {
                "lang": "en-us",
                "lang_text": "US English",
                "md5": "228741ed058893e403dce60bdd659f42",
                "name": "vosk-model-en-us-0.22",
                "size": 1913365522,
                "size_text": "1.8GiB",
                "type": "big",
                "url": "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip",
                "version": "0.22"
              },
              {
                "lang": "en-us",
                "lang_text": "US English",
                "md5": "2df36985f8ed8eb7b78476cadcdab48f",
                "name": "vosk-model-en-us-0.22-lgraph",
                "size": 130557655,
                "size_text": "124.5MiB",
                "type": "big-lgraph",
                "url": "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22-lgraph.zip",
                "version": "0.22-lgraph"
              },
              {
                "lang": "en-us",
                "lang_text": "US English",
                "md5": "not-an-md5",
                "name": "vosk-model-bad-md5",
                "size": 1000,
                "size_text": "1KiB",
                "type": "small",
                "url": "https://alphacephei.com/vosk/models/vosk-model-bad-md5.zip",
                "version": "1"
              },
              {
                "lang": "fr",
                "lang_text": "French",
                "name": "vosk-model-no-md5",
                "size": 2000,
                "size_text": "2KiB",
                "type": "small",
                "url": "https://alphacephei.com/vosk/models/vosk-model-no-md5.zip",
                "version": "1"
              },
              {
                "name": "vosk-model-missing-lang",
                "size": 3000,
                "size_text": "3KiB",
                "type": "small",
                "url": "https://alphacephei.com/vosk/models/vosk-model-missing-lang.zip",
                "version": "1"
              },
              {
                "lang": "all",
                "lang_text": "All",
                "md5": "63b868d866a756c30f048b007541fd25",
                "name": "vosk-model-spk-0.4",
                "size": 13869103,
                "size_text": "13.2MiB",
                "type": "spk",
                "url": "https://alphacephei.com/vosk/models/vosk-model-spk-0.4.zip",
                "version": "0.4"
              },
              {
                "lang": "ru",
                "lang_text": "Russian",
                "md5": "2f8b6dbf64e912f9ee7eda50ba2d3c80",
                "name": "vosk-model-tts-ru-0.9-multi",
                "size": 782787154,
                "size_text": "746.5MiB",
                "type": "tts",
                "url": "https://alphacephei.com/vosk/models/vosk-model-tts-ru-0.9-multi.zip",
                "version": "0.9-multi"
              },
              {
                "lang": "en-us",
                "lang_text": "US English",
                "name": "../../evil",
                "size": 10,
                "size_text": "10B",
                "type": "small",
                "url": "https://alphacephei.com/vosk/models/evil.zip"
              },
              {
                "lang": "en-us",
                "lang_text": "US English",
                "name": "vosk-model-http-url",
                "size": 10,
                "size_text": "10B",
                "type": "small",
                "url": "http://alphacephei.com/vosk/models/vosk-model-http-url.zip"
              },
              {
                "lang": "en-us",
                "lang_text": "US English",
                "name": "vosk-model-external-url",
                "size": 10,
                "size_text": "10B",
                "type": "small",
                "url": "https://example.com/model.zip"
              }
            ]
            """;

    @Test
    void parsesMachineReadableCatalogueAndFiltersUnsupportedEntries() throws IOException {
        List<VoskModel> models = parse(TEST_CATALOG);

        assertEquals(6, models.size());
        assertFalse(models.stream().anyMatch(m -> m.getName().contains("spk")));
        assertFalse(models.stream().anyMatch(m -> m.getName().contains("tts")));
        assertFalse(models.stream().anyMatch(m -> m.getName().equals("vosk-model-small-en-us-0.3")));
        assertFalse(models.stream().anyMatch(m -> m.getName().equals("../../evil")));
        assertTrue(models.stream().noneMatch(m -> m.getName().equals("vosk-model-http-url")));
        assertTrue(models.stream().noneMatch(m -> m.getName().equals("vosk-model-external-url")));

        VoskModel small = byName(models, "vosk-model-small-en-us-0.15");
        assertEquals("US English", small.getLanguage());
        assertEquals("39.3MiB", small.getSize());
        assertEquals(41_205_931, small.getSizeBytes());
        assertEquals("09ab50ccd62b674cbaa231b825f9c1cb", small.getChecksumMd5());
        assertEquals(VoskModel.ModelType.SMALL, small.getType());
        assertEquals("Version 0.15", small.getDescription());

        VoskModel big = byName(models, "vosk-model-en-us-0.22");
        assertEquals(VoskModel.ModelType.BIG, big.getType());
        assertEquals(1_913_365_522, big.getSizeBytes());

        VoskModel lgraph = byName(models, "vosk-model-en-us-0.22-lgraph");
        assertEquals(VoskModel.ModelType.BIG, lgraph.getType());

        VoskModel invalidChecksum = byName(models, "vosk-model-bad-md5");
        assertEquals(null, invalidChecksum.getChecksumMd5());

        VoskModel noChecksum = byName(models, "vosk-model-no-md5");
        assertEquals(null, noChecksum.getChecksumMd5());

        VoskModel missingLanguage = byName(models, "vosk-model-missing-lang");
        assertEquals("Unknown", missingLanguage.getLanguage());
    }

    @Test
    void duplicateNamesKeepFirstEntry() throws IOException {
        String json = """
                [
                  {"name":"vosk-model-dupe","size":111,"size_text":"111B","type":"small",
                   "url":"https://alphacephei.com/vosk/models/vosk-model-dupe.zip","version":"1"},
                  {"name":"vosk-model-dupe","size":222,"size_text":"222B","type":"big",
                   "url":"https://alphacephei.com/vosk/models/vosk-model-dupe.zip","version":"2"}
                ]
                """;

        List<VoskModel> models = parse(json);

        assertEquals(1, models.size());
        assertEquals(111, models.get(0).getSizeBytes());
    }

    @Test
    void bundledSnapshotIsCompleteAndUsableOffline() throws IOException {
        List<VoskModel> models = ModelRegistry.loadBundledCatalog();

        assertEquals(63, models.size(),
                "Bundled snapshot should contain only current speech-to-text models");
        VoskModel known = models.stream()
                .filter(m -> m.getName().equals("vosk-model-small-en-us-0.15"))
                .findFirst()
                .orElseThrow();

        assertEquals("US English", known.getLanguage());
        assertEquals(41_205_931, known.getSizeBytes());
        assertEquals("09ab50ccd62b674cbaa231b825f9c1cb", known.getChecksumMd5());
        assertEquals(VoskModel.ModelType.SMALL, known.getType());
        assertTrue(models.stream().noneMatch(m -> m.getName().equals("vosk-model-small-en-us-0.3")));

        for (VoskModel model : models) {
            assertTrue(VoskModel.isSafeModelName(model.getName()), model.getName());
            assertTrue(model.getDownloadUrl().startsWith("https://alphacephei.com/vosk/models/"),
                    model.getDownloadUrl());
            assertTrue(model.getSizeBytes() > 0, model.getName());
            assertNotNull(model.getChecksumMd5(), model.getName());
            assertEquals(32, model.getChecksumMd5().length(), model.getName());
        }
    }

    @Test
    void fallsBackToSnapshotWhenRemoteFetchFails() throws IOException {
        AtomicInteger remoteCalls = new AtomicInteger();
        ModelRegistry registry = new ModelRegistry(
                () -> {
                    remoteCalls.incrementAndGet();
                    throw new IOException("simulated offline");
                },
                ModelRegistry::loadBundledCatalog);

        List<VoskModel> first = registry.fetchModels(true);
        assertTrue(registry.isUsingFallbackSnapshot());
        assertFalse(first.isEmpty());
        assertEquals(1, remoteCalls.get());

        List<VoskModel> cached = registry.fetchModels(false);
        assertEquals(first.size(), cached.size());
        assertEquals(1, remoteCalls.get(), "Cached results should not re-fetch");

        registry.fetchModels(true);
        assertEquals(2, remoteCalls.get(), "forceRefresh should re-fetch");
    }

    @Test
    void fallsBackWhenRemoteReturnsEmptyList() throws IOException {
        ModelRegistry registry = new ModelRegistry(
                List::of,
                ModelRegistry::loadBundledCatalog);

        List<VoskModel> models = registry.fetchModels(true);

        assertFalse(models.isEmpty());
        assertTrue(registry.isUsingFallbackSnapshot());
    }

    @Test
    void successfulRemoteFetchDoesNotUseFallback() throws IOException {
        List<VoskModel> remoteModels = parse(TEST_CATALOG);
        ModelRegistry registry = new ModelRegistry(
                () -> remoteModels,
                () -> {
                    throw new AssertionError("Fallback must not be called");
                });

        List<VoskModel> models = registry.fetchModels(true);

        assertEquals(remoteModels.size(), models.size());
        assertFalse(registry.isUsingFallbackSnapshot());
    }

    @Test
    void throwsWhenRemoteAndSnapshotBothFail() {
        ModelRegistry registry = new ModelRegistry(
                () -> {
                    throw new IOException("offline");
                },
                () -> {
                    throw new IOException("snapshot missing");
                });

        IOException error = assertThrows(IOException.class, () -> registry.fetchModels(true));
        assertEquals(2, error.getSuppressed().length);
    }

    @Test
    void exposesLanguageGroupsAndLookup() throws IOException {
        List<VoskModel> remoteModels = parse(TEST_CATALOG);
        ModelRegistry registry = new ModelRegistry(() -> remoteModels, List::of);

        registry.fetchModels(true);

        Set<String> languages = registry.getAvailableLanguages();
        assertTrue(languages.contains("US English"));
        assertTrue(languages.contains("French"));
        assertTrue(languages.contains("Unknown"));
        assertEquals(4, registry.getModelsByLanguage("US English").size());
        assertEquals(0, registry.getModelsByLanguage("Klingon").size());
        assertTrue(registry.findModelByName("vosk-model-en-us-0.22").isPresent());
        assertTrue(registry.findModelByName("missing").isEmpty());
    }

    private static List<VoskModel> parse(String json) throws IOException {
        return ModelRegistry.parseCatalog(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static VoskModel byName(List<VoskModel> models, String name) {
        return models.stream()
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
