package atri.palaash.jvosk.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ModelMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsZeroWhenPathIsNotADirectory() throws IOException {
        Path missing = tempDir.resolve("does-not-exist");
        Path file = tempDir.resolve("a-file");
        Files.writeString(file, "nope");

        assertEquals(0, ModelMigration.migrateLegacyModels(missing));
        assertEquals(0, ModelMigration.migrateLegacyModels(file));
        assertFalse(Files.exists(missing), "Migration must not create a missing models directory");
    }
}
