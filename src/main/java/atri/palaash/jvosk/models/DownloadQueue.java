package atri.palaash.jvosk.models;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Persists the Model Manager download queue so unfinished work survives
 * application restarts. Names that are not safe model directory names are
 * dropped and never written.
 */
public final class DownloadQueue {

    static final String QUEUE_FILE = ".jvosk-download-queue.json";
    private static final int SCHEMA_VERSION = 1;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Path queuePath;
    private final List<String> names = new ArrayList<>();

    public DownloadQueue(Path modelsDirectory) {
        this.queuePath = Objects.requireNonNull(modelsDirectory, "modelsDirectory")
                .resolve(QUEUE_FILE);
        load();
    }

    public synchronized List<String> remaining() {
        return List.copyOf(names);
    }

    public synchronized boolean isEmpty() {
        return names.isEmpty();
    }

    public synchronized int size() {
        return names.size();
    }

    public synchronized void setPending(Collection<String> modelNames) throws IOException {
        names.clear();
        names.addAll(sanitize(modelNames));
        persist();
    }

    public synchronized void markCompleted(String modelName) throws IOException {
        if (modelName == null) {
            return;
        }
        names.removeIf(modelName::equals);
        persist();
    }

    public synchronized void clear() throws IOException {
        names.clear();
        persist();
    }

    private void load() {
        names.clear();
        if (!Files.isRegularFile(queuePath)) {
            return;
        }
        try {
            QueueFile file = OBJECT_MAPPER.readValue(queuePath.toFile(), QueueFile.class);
            if (file.schemaVersion != SCHEMA_VERSION || file.modelNames == null) {
                return;
            }
            names.addAll(sanitize(file.modelNames));
        } catch (IOException | RuntimeException e) {
            System.err.println("Ignoring invalid download queue: " + e.getMessage());
        }
    }

    private void persist() throws IOException {
        QueueFile file = new QueueFile();
        file.schemaVersion = SCHEMA_VERSION;
        file.modelNames = List.copyOf(names);
        Path temporaryPath = queuePath.resolveSibling(QUEUE_FILE + ".part");
        try {
            OBJECT_MAPPER.writeValue(temporaryPath.toFile(), file);
            try {
                Files.move(temporaryPath, queuePath,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryPath, queuePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(temporaryPath);
            } catch (IOException ignored) {
            }
        }
    }

    private static List<String> sanitize(Collection<String> modelNames) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (modelNames == null) {
            return List.of();
        }
        for (String name : modelNames) {
            if (VoskModel.isSafeModelName(name)) {
                unique.add(name);
            }
        }
        return new ArrayList<>(unique);
    }

    static final class QueueFile {
        public int schemaVersion;
        public List<String> modelNames;
    }
}
