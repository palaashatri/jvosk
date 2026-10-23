package atri.palaash.jvosk.stt;

import atri.palaash.jvosk.models.ModelManager;
import atri.palaash.jvosk.models.VoskModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.vosk.Model;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoskModelIntegrationIT {

    private static final String MODEL_NAME = "vosk-model-small-en-us-0.15";
    private static final long MODEL_SIZE = 41_205_931L;
    private static final String MODEL_MD5 = "09ab50ccd62b674cbaa231b825f9c1cb";
    private static final String MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";

    @TempDir
    Path tempDir;

    @Test
    void downloadsExtractsLoadsAndTranscribesShortWav() throws IOException, InterruptedException,
            URISyntaxException {
        ModelManager manager = new ModelManager(tempDir.resolve("models").toString());
        VoskModel model = new VoskModel.Builder()
                .name(MODEL_NAME)
                .language("US English")
                .size("39.3MiB")
                .sizeBytes(MODEL_SIZE)
                .checksumMd5(MODEL_MD5)
                .catalogueVersion("0.15")
                .downloadUrl(MODEL_URL)
                .type(VoskModel.ModelType.SMALL)
                .build();

        manager.downloadModel(model, null);
        assertTrue(manager.getInstalledModels().stream()
                .anyMatch(installed -> installed.getName().equals(MODEL_NAME)));

        Model loadedModel = manager.loadModel(MODEL_NAME);
        assertNotNull(loadedModel);
        try {
            VoskTranscriber transcriber = new VoskTranscriber(loadedModel);
            var sampleUrl = VoskModelIntegrationIT.class.getResource("/vosk-test.wav");
            assertNotNull(sampleUrl, "Missing attributed Vosk sample WAV");
            File sample = new File(sampleUrl.toURI());
            AtomicReference<String> transcript = new AtomicReference<>("");
            transcriber.transcribeFile(sample,
                    partial -> transcript.updateAndGet(previous -> previous + " " + partial));

            String recognized = transcript.get().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ");
            assertFalse(recognized.isBlank(), "The sample should produce speech recognition output");
            assertTrue(recognized.contains("one") || recognized.contains("zero"),
                    "Expected the known English sample speech, got: " + transcript.get());
        } finally {
            manager.unloadModel(MODEL_NAME);
        }
    }
}
