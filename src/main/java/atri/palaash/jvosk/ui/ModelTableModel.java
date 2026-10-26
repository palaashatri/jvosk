package atri.palaash.jvosk.ui;

import atri.palaash.jvosk.models.VoskModel;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Table model for the Model Manager: catalogue rows plus partial-download
 * status. Extracted so Swing tests can exercise status text without the dialog.
 */
public final class ModelTableModel extends AbstractTableModel {
    private final String[] columnNames = {"Name", "Language", "Size", "Type", "Status", "Description"};
    private List<VoskModel> models = new ArrayList<>();
    private Map<String, Long> partialDownloads = Map.of();

    public void setModels(List<VoskModel> models, Map<String, Long> partialDownloads) {
        this.models = new ArrayList<>(models);
        this.partialDownloads = Map.copyOf(partialDownloads);
        fireTableDataChanged();
    }

    public boolean hasPartialDownload(String modelName) {
        return partialDownloads.containsKey(modelName);
    }

    public VoskModel getModel(int row) {
        return models.get(row);
    }

    @Override
    public int getRowCount() {
        return models.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        VoskModel model = models.get(rowIndex);

        return switch (columnIndex) {
            case 0 -> model.getName();
            case 1 -> model.getLanguage();
            case 2 -> model.getSize();
            case 3 -> model.getType().toString();
            case 4 -> statusFor(model);
            case 5 -> model.getDescription();
            default -> "";
        };
    }

    private String statusFor(VoskModel model) {
        Long partialSize = partialDownloads.get(model.getName());
        if (model.isInstalled()) {
            return "Installed";
        }
        if (partialSize == null) {
            return "Available";
        }
        if (model.getSizeBytes() > 0) {
            long percent = Math.min(99, partialSize * 100 / model.getSizeBytes());
            return String.format("Partial (%d%%)", percent);
        }
        return "Partial";
    }
}
