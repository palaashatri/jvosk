package atri.palaash.jvosk.ui;

import atri.palaash.jvosk.models.DownloadManager;
import atri.palaash.jvosk.models.DownloadQueue;
import atri.palaash.jvosk.models.VoskModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.JTable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelManagerUiTest {

    @TempDir
    Path tempDir;

    @Test
    void tableShowsPartialDownloadStatus() {
        VoskModel available = model("vosk-model-small-en-us-0.15", false, 300);
        ModelTableModel tableModel = new ModelTableModel();
        JTable table = new JTable(tableModel);
        tableModel.setModels(List.of(available), Map.of(available.getName(), 99L));

        assertEquals(1, table.getRowCount());
        assertEquals("Partial (33%)", table.getValueAt(0, 4));
        assertTrue(tableModel.hasPartialDownload(available.getName()));
    }

    @Test
    void downloadButtonUsesUpdateReinstallAndResumeLabels() {
        VoskModel installed = model("vosk-model-installed", true, 100);
        VoskModel downloading = model("vosk-model-partial", false, 100);

        assertEquals("Update Model", ModelDownloadActions.downloadButtonText(
                installed, Set.of(installed.getName()), Set.of(), false));
        assertEquals("Reinstall Model", ModelDownloadActions.downloadButtonText(
                installed, Set.of(), Set.of(installed.getName()), false));
        assertEquals("Resume Download", ModelDownloadActions.downloadButtonText(
                downloading, Set.of(), Set.of(), true));
        assertEquals("Download Model", ModelDownloadActions.downloadButtonText(
                downloading, Set.of(), Set.of(), false));

        assertTrue(ModelDownloadActions.isDownloadCandidate(
                installed, Set.of(installed.getName()), Set.of()));
        assertTrue(ModelDownloadActions.isDownloadCandidate(
                installed, Set.of(), Set.of(installed.getName())));
        assertFalse(ModelDownloadActions.isDownloadCandidate(
                installed, Set.of(), Set.of()));
    }

    @Test
    void queueCancellationKeepsRemainingNamesForResume() throws Exception {
        Path models = tempDir.resolve("models");
        Files.createDirectories(models);
        DownloadQueue queue = new DownloadQueue(models);
        queue.setPending(List.of("vosk-model-a", "vosk-model-b", "vosk-model-c"));

        JButton cancel = new JButton("Cancel Download");
        cancel.addActionListener(e -> DownloadManager.getInstance().cancelCurrentDownload());

        queue.markCompleted("vosk-model-a");
        cancel.doClick();
        assertTrue(DownloadManager.getInstance().isCancellationRequested());

        DownloadQueue restarted = new DownloadQueue(models);
        assertEquals(List.of("vosk-model-b", "vosk-model-c"), restarted.remaining());
        DownloadManager.getInstance().clearActiveDownload();
    }

    private static VoskModel model(String name, boolean installed, long sizeBytes) {
        return new VoskModel.Builder()
                .name(name)
                .language("English")
                .size("1M")
                .sizeBytes(sizeBytes)
                .description("test")
                .downloadUrl("https://alphacephei.com/vosk/models/" + name + ".zip")
                .type(VoskModel.ModelType.SMALL)
                .isInstalled(installed)
                .installedVersion(installed ? "1" : null)
                .build();
    }
}
