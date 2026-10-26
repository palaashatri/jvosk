package atri.palaash.jvosk.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppPreferencesTest {

    @Test
    void remapsSelectedModelOnlyWhenItIsInsideTheModelsDirectory() {
        String original = AppPreferences.getSelectedModel();
        Path models = Path.of(System.getProperty("java.io.tmpdir"), "jvosk-prefs-models");
        try {
            AppPreferences.setSelectedModel(models.resolve("vosk-model-old").toString());
            AppPreferences.remapSelectedModel(models, Map.of("vosk-model-old", "vosk-model-new"));
            assertEquals(models.resolve("vosk-model-new").toAbsolutePath().normalize().toString(),
                    AppPreferences.getSelectedModel());

            AppPreferences.setSelectedModel("/somewhere/vosk-model-old");
            AppPreferences.remapSelectedModel(models, Map.of("vosk-model-old", "vosk-model-new"));
            assertEquals("/somewhere/vosk-model-old", AppPreferences.getSelectedModel());
        } finally {
            AppPreferences.setSelectedModel(original);
        }
    }
}
