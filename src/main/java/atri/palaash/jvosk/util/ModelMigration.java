package atri.palaash.jvosk.util;

import atri.palaash.jvosk.models.ModelManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Seeds {@code .jvosk-install.json} for valid Vosk model directories that were
 * installed before metadata persistence. Catalogue fields are copied from a
 * matching catalogue entry so {@link ModelManager} can track updates without a
 * reinstall. Existing metadata, staging/backup directories and invalid models
 * are left unchanged.
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
