package atri.palaash.jvosk.util;

import atri.palaash.jvosk.models.ModelManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Seeds {@code .jvosk-install.json} for valid Vosk model directories that were
 * installed before metadata persistence. Only the schema version and model name
 * are recorded; catalogue version, size and checksum stay unset until a verified
 * install, so a matching directory name is never treated as current. Existing
 * metadata, staging/backup directories and invalid models are left unchanged.
 */
public final class ModelMigration {
    private ModelMigration() {}

    /**
     * Scan {@code modelsDir} and write install metadata for eligible legacy
     * installs. Returns {@code 0} when the path is not a directory.
     */
    public static int migrateLegacyModels(Path modelsDir) throws IOException {
        if (modelsDir == null || !Files.isDirectory(modelsDir)) {
            return 0;
        }
        return new ModelManager(modelsDir.toString()).migrateLegacyInstalls();
    }
}
