package atri.palaash.jvosk.util;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Utility to seed metadata for legacy model installs that predate persistence.
 * Scans ~/.jvosk/models and creates basic metadata files if missing.
 */
public final class ModelMigration {
    private ModelMigration() {}

    public static int migrateLegacyModels(Path modelsDir) throws IOException {
        if (!Files.isDirectory(modelsDir)) return 0;
        int count = 0;
        try (Stream<Path> dirs = Files.list(modelsDir)) {
            for (Path model : dirs.filter(Files::isDirectory).toList()) {
                Path meta = model.resolve("model-meta.json");
                if (!Files.exists(meta)) {
                    // Minimal metadata stub; real impl would compute checksum/size
                    String stub = "{\"version\":\"legacy\",\"size\":0,\"checksum\":\"unknown\"}";
                    Files.writeString(meta, stub);
                    count++;
                }
            }
        }
        return count;
    }
}
