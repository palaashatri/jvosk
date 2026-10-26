package atri.palaash.jvosk.ui;

import atri.palaash.jvosk.models.DownloadManager;
import atri.palaash.jvosk.models.VoskModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelManagerUiTest {

    @AfterEach
    void resetDownloadManager() {
        DownloadManager.getInstance().clearActiveDownload();
    }

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
    void tableDistinguishesInstalledModelsOutsideTheCatalogue() {
        VoskModel orphan = installedWithoutDownloadSource("vosk-model-orphan");
        ModelTableModel tableModel = new ModelTableModel();
        tableModel.setModels(List.of(orphan), Map.of(), Set.of("vosk-model-current"));

        assertEquals("Installed (not in catalogue)", tableModel.getValueAt(0, 4));
        assertFalse(ModelDownloadActions.isDownloadCandidate(
                orphan, Set.of(orphan.getName()), Set.of(orphan.getName())),
                "A model without a catalogue archive must not offer reinstall");
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
    void resumableModelsDropsUnknownDuplicatesAndInstalled() {
        VoskModel pending = model("vosk-model-a", false, 100);
        VoskModel installed = model("vosk-model-installed", true, 100);
        VoskModel update = model("vosk-model-update", true, 100);
        List<VoskModel> catalogue = List.of(pending, installed, update);

        List<VoskModel> resume = ModelDownloadActions.resumableModels(
                List.of("vosk-model-a", "missing", "vosk-model-a", "vosk-model-installed", "vosk-model-update"),
                catalogue,
                Set.of("vosk-model-update"),
                Set.of());

        assertEquals(List.of("vosk-model-a", "vosk-model-update"),
                resume.stream().map(VoskModel::getName).toList());
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

    private static VoskModel installedWithoutDownloadSource(String name) {
        return new VoskModel.Builder()
                .name(name)
                .language("English")
                .size("Unknown")
                .sizeBytes(0)
                .description("test")
                .downloadUrl("installed://local")
                .type(VoskModel.ModelType.SMALL)
                .isInstalled(true)
                .installedVersion(name)
                .build();
    }
}
