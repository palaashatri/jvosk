package atri.palaash.jvosk.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadQueueTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsRemainingNamesAcrossRestartAndCancellation() throws IOException {
        Path models = tempDir.resolve("models");
        Files.createDirectories(models);

        DownloadQueue queue = new DownloadQueue(models);
        queue.setPending(List.of(
                "vosk-model-one",
                "../evil",
                "vosk-model-two",
                "vosk-model-one",
                ".hidden"));
        assertEquals(List.of("vosk-model-one", "vosk-model-two"), queue.remaining());

        queue.markCompleted("vosk-model-one");
        assertEquals(List.of("vosk-model-two"), queue.remaining());

        DownloadQueue restarted = new DownloadQueue(models);
        assertEquals(List.of("vosk-model-two"), restarted.remaining());
        assertTrue(Files.isRegularFile(models.resolve(".jvosk-download-queue.json")));
        assertFalse(Files.exists(models.resolve(".jvosk-download-queue.json.part")));

        restarted.clear();
        assertTrue(new DownloadQueue(models).isEmpty());
    }
}
