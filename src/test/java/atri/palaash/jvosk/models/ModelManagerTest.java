package atri.palaash.jvosk.models;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelManagerTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private String baseUrl;
    private byte[] goodZip;
    private final AtomicInteger rangeRequests = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        goodZip = modelZip("vosk-model-good");
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/good.zip", exchange -> send(exchange, goodZip, false));
        server.createContext("/tampered.zip", exchange -> send(exchange, tampered(goodZip), false));
        server.createContext("/chunked.zip", exchange -> send(exchange, goodZip, true));
        server.createContext("/range.zip", this::sendRange);
        server.createContext("/slow.zip", this::sendSlow);
        server.createContext("/ignore-range.zip", exchange -> send(exchange, goodZip, false));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        DownloadManager.getInstance().clearActiveDownload();
    }

    @Test
    void extractsAndFlattensModelDirectory() throws IOException {
        Path zip = tempDir.resolve("model.zip");
        Files.write(zip, goodZip);

        Path destination = tempDir.resolve("extracted");
        ModelManager manager = new ModelManager(tempDir.resolve("models").toString());
        manager.extractZip(zip, destination);

        assertTrue(Files.isRegularFile(destination.resolve("am/final.mdl")));
        assertTrue(Files.isRegularFile(destination.resolve("conf/mfcc.conf")));
        assertTrue(Files.isRegularFile(destination.resolve("graph/HCLG.fst")));
        assertFalse(Files.exists(destination.resolve("vosk-model-good")));
    }

    @Test
    void rejectsZipSlipEntries() throws IOException {
        byte[] zipBytes = zip(Map.of(
                "../escape.txt", "escaped".getBytes(StandardCharsets.UTF_8),
                "am/final.mdl", "ok".getBytes(StandardCharsets.UTF_8)));
        Path zip = tempDir.resolve("evil.zip");
        Files.write(zip, zipBytes);

        Path destination = tempDir.resolve("dest");
        ModelManager manager = new ModelManager(tempDir.resolve("models").toString());

        IOException error = assertThrows(IOException.class, () -> manager.extractZip(zip, destination));
        assertTrue(error.getMessage().contains("Invalid zip entry"));
        assertFalse(Files.exists(tempDir.resolve("escape.txt")));
        assertFalse(Files.exists(destination.getParent().resolve("escape.txt")));
    }

    @Test
    void rejectsZipSlipUsingWindowsSeparator() throws IOException {
        byte[] zipBytes = zip(Map.of(
                "..\\escape.txt", "escaped".getBytes(StandardCharsets.UTF_8)));
        Path zip = tempDir.resolve("evil-backslash.zip");
        Files.write(zip, zipBytes);

        Path destination = tempDir.resolve("dest");
        ModelManager manager = new ModelManager(tempDir.resolve("models").toString());

        assertThrows(IOException.class, () -> manager.extractZip(zip, destination));
        assertFalse(Files.exists(tempDir.resolve("escape.txt")));
    }

    @Test
    void downloadsVerifiesAndInstallsModel() throws IOException {
        ModelManager manager = new ModelManager(tempDir.resolve("models").toString());
        List<Integer> progress = new ArrayList<>();

        VoskModel model = downloadModel("vosk-model-good", "/good.zip", goodZip, md5(goodZip));
        manager.downloadModel(model, progress::add);

        Path installed = manager.getModelsDirectory().resolve("vosk-model-good");
        assertTrue(Files.isRegularFile(installed.resolve("am/final.mdl")));
        assertTrue(Files.isRegularFile(installed.resolve("graph/HCLG.fst")));
        assertTrue(manager.getInstalledModels().stream()
                .anyMatch(m -> m.getName().equals("vosk-model-good") && m.isInstalled()));
        assertEquals(100, progress.get(progress.size() - 1));

        assertNoDownloadArtifacts(manager.getModelsDirectory());
    }

    @Test
    void resumesPartialDownloadAndSurfacesItsProgress() throws IOException {
        ModelManager manager = new ModelManager(tempDir.resolve("models").toString());
        VoskModel model = downloadModel("vosk-model-resume", "/range.zip", goodZip, md5(goodZip));
        Path partial = manager.getModelsDirectory()
                .resolve(".jvosk-download-vosk-model-resume.zip.part");
        int existingBytes = goodZip.length / 3;
        Files.write(partial, Arrays.copyOf(goodZip, existingBytes));

        assertEquals(existingBytes, manager.getPartialDownloads().get("vosk-model-resume"));
        manager.downloadModel(model, null);

        assertEquals(1, rangeRequests.get(), "Expected an HTTP Range request for the existing bytes");
        assertTrue(Files.isRegularFile(manager.getModelsDirectory()
                .resolve("vosk-model-resume/am/final.mdl")));
        assertFalse(manager.getPartialDownloads().containsKey("vosk-model-resume"));
        assertNoDownloadArtifacts(manager.getModelsDirectory());
    }

    @Test
    void cancellationKeepsPartialBytesThatCanBeResumed() throws IOException {
        ModelManager manager = new ModelManager(tempDir.resolve("models").toString());
        VoskModel slowModel = downloadModel("vosk-model-cancel", "/slow.zip", goodZip, md5(goodZip));
        AtomicBoolean cancelled = new AtomicBoolean();

        IOException error = assertThrows(IOException.class, () -> manager.downloadModel(slowModel, progress -> {
            if (cancelled.compareAndSet(false, true)) {
                DownloadManager.getInstance().cancelCurrentDownload();
            }
        }));
        assertTrue(error.getMessage().contains("cancelled"));

        long partialSize = manager.getPartialDownloads().get("vosk-model-cancel");
        assertTrue(partialSize > 0 && partialSize < goodZip.length);
        assertFalse(Files.exists(manager.getModelsDirectory().resolve("vosk-model-cancel")));

        VoskModel resumableModel = downloadModel("vosk-model-cancel", "/range.zip", goodZip, md5(goodZip));
        manager.downloadModel(resumableModel, null);
        assertEquals(1, rangeRequests.get());
        assertTrue(Files.isRegularFile(manager.getModelsDirectory()
                .resolve("vosk-model-cancel/am/final.mdl")));
        assertFalse(manager.getPartialDownloads().containsKey("vosk-model-cancel"));
    }

    @Test
    void restartsFromZeroWhenServerIgnoresRange() throws IOException {
        ModelManager manager = new ModelManager(tempDir.resolve("models").toString());
        VoskModel model = downloadModel("vosk-model-ignore-range", "/ignore-range.zip", goodZip, md5(goodZip));
        Path partial = manager.getModelsDirectory()
                .resolve(".jvosk-download-vosk-model-ignore-range.zip.part");
        Files.write(partial, Arrays.copyOf(goodZip, goodZip.length / 3));

        manager.downloadModel(model, null);

        assertTrue(Files.isRegularFile(manager.getModelsDirectory()
                .resolve("vosk-model-ignore-range/am/final.mdl")));
        assertNoDownloadArtifacts(manager.getModelsDirectory());
    }

    @Test
    void persistsInstallMetadataAndDetectsCatalogueUpdatesAfterRestart() throws IOException {
        Path modelsDirectory = tempDir.resolve("models");
        VoskModel installedModel = modelWithMetadata(
                "vosk-model-good", "/good.zip", goodZip, "1.0", md5(goodZip));
        ModelManager installer = new ModelManager(modelsDirectory.toString());
        installer.downloadModel(installedModel, null);

        ModelManager restarted = new ModelManager(modelsDirectory.toString());
        VoskModel restored = restarted.getInstalledModels().stream()
                .filter(model -> model.getName().equals("vosk-model-good"))
                .findFirst()
                .orElseThrow();
        assertEquals("1.0", restored.getCatalogueVersion());
        assertEquals(goodZip.length, restored.getSizeBytes());
        assertEquals(md5(goodZip), restored.getChecksumMd5());
        assertTrue(restarted.getInstalledModelsWithoutUpdateMetadata().isEmpty());

        VoskModel sameCatalogueEntry = modelWithMetadata(
                "vosk-model-good", "/good.zip", goodZip, "1.0", md5(goodZip));
        ModelManager unchanged = new ModelManager(modelsDirectory.toString(),
                new ModelRegistry(() -> List.of(sameCatalogueEntry), List::of));
        assertTrue(unchanged.checkForUpdates().isEmpty());

        VoskModel changedCatalogueEntry = modelWithMetadata(
                "vosk-model-good", "/good.zip", goodZip, "2.0", md5(tampered(goodZip)));
        ModelManager changed = new ModelManager(modelsDirectory.toString(),
                new ModelRegistry(() -> List.of(changedCatalogueEntry), List::of));
        Map<String, VoskModel> updates = changed.checkForUpdates();
        assertTrue(updates.containsKey("vosk-model-good"));
        assertEquals("2.0", updates.get("vosk-model-good").getCatalogueVersion());
    }

    @Test
    void failedReplacementLeavesExistingInstallationAndMetadataIntact() throws IOException {
        Path modelsDirectory = tempDir.resolve("models");
        ModelManager manager = new ModelManager(modelsDirectory.toString());
        VoskModel original = modelWithMetadata(
                "vosk-model-good", "/good.zip", goodZip, "1.0", md5(goodZip));
        manager.downloadModel(original, null);

        VoskModel invalidReplacement = modelWithMetadata(
                "vosk-model-good", "/tampered.zip", tampered(goodZip), "2.0", md5(goodZip));
        assertThrows(IOException.class, () -> manager.downloadModel(invalidReplacement, null));

        ModelManager restarted = new ModelManager(modelsDirectory.toString());
        VoskModel stillInstalled = restarted.getInstalledModels().stream()
                .filter(model -> model.getName().equals("vosk-model-good"))
                .findFirst()
                .orElseThrow();
        assertEquals("1.0", stillInstalled.getCatalogueVersion());
        assertEquals(md5(goodZip), stillInstalled.getChecksumMd5());
        assertTrue(Files.isRegularFile(modelsDirectory.resolve("vosk-model-good/am/final.mdl")));
        assertNoDownloadArtifacts(modelsDirectory);
    }

    @Test
    void rejectsChecksumMismatchAndCleansUp() throws IOException {
        ModelManager manager = new ModelManager(tempDir.resolve("models").toString());

        VoskModel model = downloadModel("vosk-model-bad-checksum", "/tampered.zip",
                tampered(goodZip), md5(goodZip));

        IOException error = assertThrows(IOException.class, () -> manager.downloadModel(model, null));
        assertTrue(error.getMessage().contains("Checksum"));

        assertFalse(Files.exists(manager.getModelsDirectory().resolve("vosk-model-bad-checksum")));
        assertTrue(manager.getInstalledModels().isEmpty());
        assertNoDownloadArtifacts(manager.getModelsDirectory());
    }

    @Test
    void verifiesCatalogueSizeWhenServerOmitsContentLength() throws IOException {
        ModelManager manager = new ModelManager(tempDir.resolve("models").toString());

        VoskModel model = new VoskModel.Builder()
                .name("vosk-model-wrong-size")
                .size("1M")
                .sizeBytes(goodZip.length + 1L)
                .downloadUrl(baseUrl + "/chunked.zip")
                .type(VoskModel.ModelType.SMALL)
                .build();

        IOException error = assertThrows(IOException.class, () -> manager.downloadModel(model, null));
        assertTrue(error.getMessage().contains("size"));

        assertFalse(Files.exists(manager.getModelsDirectory().resolve("vosk-model-wrong-size")));
        assertNoDownloadArtifacts(manager.getModelsDirectory());
    }

    @Test
    void rejectsServerSizeThatContradictsCatalogue() throws IOException {
        ModelManager manager = new ModelManager(tempDir.resolve("models").toString());

        VoskModel model = new VoskModel.Builder()
                .name("vosk-model-size-conflict")
                .size("1M")
                .sizeBytes(goodZip.length + 1L)
                .downloadUrl(baseUrl + "/good.zip")
                .type(VoskModel.ModelType.SMALL)
                .build();

        IOException error = assertThrows(IOException.class, () -> manager.downloadModel(model, null));
        assertTrue(error.getMessage().contains("catalogue"));

        assertFalse(Files.exists(manager.getModelsDirectory().resolve("vosk-model-size-conflict")));
        assertNoDownloadArtifacts(manager.getModelsDirectory());
    }

    @Test
    void rejectsPathTraversalInModelOperations() {
        ModelManager manager = new ModelManager(tempDir.resolve("models").toString());

        assertThrows(IOException.class, () -> manager.deleteModel("../outside"));
        assertThrows(IOException.class, () -> manager.loadModel("../outside"));
        assertThrows(IOException.class, () -> manager.deleteModel("dir/model"));
    }

    @Test
    void skipsStagingAndBackupDirectoriesWhenScanning() throws IOException {
        Path models = tempDir.resolve("models");
        createValidModel(models.resolve(".jvosk-extract-123"));
        createValidModel(models.resolve(".jvosk-backup-123"));
        createValidModel(models.resolve("vosk-model-installed"));

        ModelManager manager = new ModelManager(models.toString());

        assertEquals(1, manager.getInstalledModels().size());
        assertEquals("vosk-model-installed", manager.getInstalledModels().get(0).getName());
    }

    @Test
    void migrateLegacyInstallsWritesStubWithoutTrustingCatalogueIdentity() throws IOException {
        Path models = tempDir.resolve("models");
        createValidModel(models.resolve("vosk-model-good"));
        createValidModel(models.resolve(".jvosk-extract-123"));
        Files.createDirectories(models.resolve("not-a-model"));
        createValidModel(models.resolve("vosk-model-unknown"));
        Files.writeString(models.resolve("vosk-model-unknown/.jvosk-install.json"),
                "{\"schemaVersion\":2,\"modelName\":\"vosk-model-unknown\"}");

        VoskModel catalogue = modelWithMetadata(
                "vosk-model-good", "/good.zip", goodZip, "1.0", md5(goodZip));
        ModelRegistry registry = new ModelRegistry(() -> List.of(catalogue), List::of);
        ModelManager manager = new ModelManager(models.toString(), registry);

        assertEquals(1, manager.migrateLegacyInstalls());
        assertEquals(0, manager.migrateLegacyInstalls());
        assertEquals(List.of("vosk-model-good", "vosk-model-unknown"),
                manager.getInstalledModelsWithoutUpdateMetadata().stream()
                        .map(VoskModel::getName)
                        .sorted()
                        .toList());

        Path metadata = models.resolve("vosk-model-good/.jvosk-install.json");
        assertTrue(Files.isRegularFile(metadata));
        String json = Files.readString(metadata);
        assertTrue(json.contains("\"schemaVersion\" : 1") || json.contains("\"schemaVersion\":1"));
        assertTrue(json.contains("vosk-model-good"));
        assertFalse(json.contains("1.0"));
        assertFalse(json.contains(md5(goodZip)));
        assertEquals("{\"schemaVersion\":2,\"modelName\":\"vosk-model-unknown\"}",
                Files.readString(models.resolve("vosk-model-unknown/.jvosk-install.json")));
        assertFalse(Files.exists(models.resolve(".jvosk-extract-123/.jvosk-install.json")));
        assertFalse(Files.exists(models.resolve("not-a-model/.jvosk-install.json")));
        assertFalse(Files.exists(models.resolve("vosk-model-good/model-meta.json")));

        ModelManager restarted = new ModelManager(models.toString(), registry);
        VoskModel restored = restarted.getInstalledModels().stream()
                .filter(model -> model.getName().equals("vosk-model-good"))
                .findFirst()
                .orElseThrow();
        assertEquals(null, restored.getCatalogueVersion());
        assertEquals(0, restored.getSizeBytes());
        assertEquals(null, restored.getChecksumMd5());
        assertTrue(restarted.checkForUpdates().isEmpty());
    }

    @Test
    void keepsInstalledModelsThatAreNoLongerInCatalogue() throws IOException {
        Path models = tempDir.resolve("models");
        createValidModel(models.resolve("vosk-model-current"));
        createValidModel(models.resolve("vosk-model-legacy"));

        VoskModel catalogueModel = new VoskModel.Builder()
                .name("vosk-model-current")
                .language("English")
                .size("1MiB")
                .sizeBytes(1024)
                .checksumMd5("09ab50ccd62b674cbaa231b825f9c1cb")
                .downloadUrl("https://alphacephei.com/vosk/models/vosk-model-current.zip")
                .type(VoskModel.ModelType.SMALL)
                .build();
        ModelRegistry registry = new ModelRegistry(() -> List.of(catalogueModel), List::of);
        ModelManager manager = new ModelManager(models.toString(), registry);

        List<VoskModel> available = manager.getAvailableModels(true);

        assertEquals(2, available.size());
        assertEquals(2, manager.getInstalledModelsWithoutUpdateMetadata().size());
        VoskModel current = available.stream()
                .filter(m -> m.getName().equals("vosk-model-current"))
                .findFirst()
                .orElseThrow();
        assertTrue(current.isInstalled());
        assertEquals("09ab50ccd62b674cbaa231b825f9c1cb", current.getChecksumMd5());

        VoskModel legacy = available.stream()
                .filter(m -> m.getName().equals("vosk-model-legacy"))
                .findFirst()
                .orElseThrow();
        assertTrue(legacy.isInstalled());
    }

    @Test
    void refusesNonHttpDownloadUrls() throws IOException {
        ModelManager manager = new ModelManager(tempDir.resolve("models").toString());
        VoskModel model = new VoskModel.Builder()
                .name("vosk-model-bad-url")
                .size("1K")
                .downloadUrl("not a url")
                .type(VoskModel.ModelType.SMALL)
                .build();

        assertThrows(IOException.class, () -> manager.downloadModel(model, null));
        assertNoDownloadArtifacts(manager.getModelsDirectory());
    }

    @Test
    void refusesFileDownloadUrls() throws IOException {
        ModelManager manager = new ModelManager(tempDir.resolve("models").toString());
        VoskModel model = new VoskModel.Builder()
                .name("vosk-model-file-url")
                .size("1K")
                .downloadUrl("file:///tmp/model.zip")
                .type(VoskModel.ModelType.SMALL)
                .build();

        IOException error = assertThrows(IOException.class, () -> manager.downloadModel(model, null));
        assertTrue(error.getMessage().contains("scheme"));
        assertNoDownloadArtifacts(manager.getModelsDirectory());
    }

    private VoskModel downloadModel(String name, String path, byte[] archive, String checksum) {
        return new VoskModel.Builder()
                .name(name)
                .language("English")
                .size("1M")
                .sizeBytes(archive.length)
                .downloadUrl(baseUrl + path)
                .checksumMd5(checksum)
                .type(VoskModel.ModelType.SMALL)
                .build();
    }

    private VoskModel modelWithMetadata(String name, String path, byte[] archive,
                                        String version, String checksum) {
        return new VoskModel.Builder()
                .name(name)
                .language("English")
                .size("1M")
                .sizeBytes(archive.length)
                .downloadUrl(baseUrl + path)
                .checksumMd5(checksum)
                .catalogueVersion(version)
                .type(VoskModel.ModelType.SMALL)
                .build();
    }

    private static void send(HttpExchange exchange, byte[] body, boolean chunked) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/zip");
        exchange.sendResponseHeaders(200, chunked ? 0 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        } finally {
            exchange.close();
        }
    }

    private void sendRange(HttpExchange exchange) throws IOException {
        String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            send(exchange, goodZip, false);
            return;
        }

        rangeRequests.incrementAndGet();
        int offset = Integer.parseInt(rangeHeader.substring("bytes=".length(), rangeHeader.length() - 1));
        if (offset >= goodZip.length) {
            exchange.sendResponseHeaders(416, -1);
            exchange.close();
            return;
        }

        exchange.getResponseHeaders().add("Content-Type", "application/zip");
        exchange.getResponseHeaders().add("Content-Range",
                "bytes " + offset + "-" + (goodZip.length - 1) + "/" + goodZip.length);
        exchange.sendResponseHeaders(206, goodZip.length - offset);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(goodZip, offset, goodZip.length - offset);
        } finally {
            exchange.close();
        }
    }

    private void sendSlow(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/zip");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            for (int offset = 0; offset < goodZip.length; offset += 64) {
                int length = Math.min(64, goodZip.length - offset);
                out.write(goodZip, offset, length);
                out.flush();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Test server interrupted", e);
                }
            }
        } finally {
            exchange.close();
        }
    }

    private static void createValidModel(Path root) throws IOException {
        Files.createDirectories(root.resolve("am"));
        Files.createDirectories(root.resolve("conf"));
        Files.createDirectories(root.resolve("graph"));
        Files.writeString(root.resolve("am/final.mdl"), "am");
        Files.writeString(root.resolve("conf/mfcc.conf"), "conf");
        Files.writeString(root.resolve("graph/HCLG.fst"), "graph");
    }

    private static byte[] modelZip(String root) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(root + "/am/final.mdl", "am".getBytes(StandardCharsets.UTF_8));
        entries.put(root + "/conf/mfcc.conf", "conf".getBytes(StandardCharsets.UTF_8));
        entries.put(root + "/graph/HCLG.fst", "graph".getBytes(StandardCharsets.UTF_8));
        return zip(entries);
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] tampered(byte[] original) {
        byte[] copy = original.clone();
        // Flip a byte in the payload while keeping the length (and zip
        // structure) intact, so only the checksum can catch the corruption.
        copy[copy.length / 2] ^= 0x01;
        return copy;
    }

    private static String md5(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static void assertNoDownloadArtifacts(Path modelsDirectory) throws IOException {
        try (var entries = Files.list(modelsDirectory)) {
            List<String> leftovers = entries
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(".jvosk-") || name.endsWith(".zip") || name.endsWith(".part"))
                    .toList();
            assertTrue(leftovers.isEmpty(), "Leftover download artifacts: " + leftovers);
        }
    }
}
