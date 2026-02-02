package atri.palaash.jvosk.ui;

import atri.palaash.jvosk.models.DownloadManager;
import atri.palaash.jvosk.models.ModelManager;
import atri.palaash.jvosk.models.VoskModel;
import atri.palaash.jvosk.util.NetworkUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Dialog for managing Vosk models - viewing, downloading, and deleting models.
 */
public class ModelManagerDialog extends JDialog {
    
    private final ModelManager modelManager;
    private final JTable modelTable;
    private final ModelTableModel tableModel;
    private final JButton downloadButton;
    private final JButton deleteButton;
    private final JButton useButton;
    private final JButton refreshButton;
    private final JButton cancelDownloadButton;
    private final JProgressBar downloadProgress;
    private final JLabel statusLabel;
    private VoskModel selectedModel = null;
    private CompletableFuture<Void> currentDownloadTask = null;
    
    public ModelManagerDialog(Frame owner, ModelManager modelManager) {
        super(owner, "Model Manager", true);
        this.modelManager = modelManager;
        
        setSize(1000, 600);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                onDialogClosing();
            }
        });
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Title and info
        JLabel titleLabel = new JLabel("Vosk Model Manager");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(titleLabel, BorderLayout.WEST);
        
        JButton checkUpdatesButton = new JButton("Check for Updates");
        checkUpdatesButton.addActionListener(e -> checkForUpdates());
        headerPanel.add(checkUpdatesButton, BorderLayout.EAST);
        
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        // Table
        tableModel = new ModelTableModel();
        modelTable = new JTable(tableModel);
        modelTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modelTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonStates();
            }
        });
        
        // Set column widths
        modelTable.getColumnModel().getColumn(0).setPreferredWidth(250); // Name
        modelTable.getColumnModel().getColumn(1).setPreferredWidth(100); // Language
        modelTable.getColumnModel().getColumn(2).setPreferredWidth(80);  // Size
        modelTable.getColumnModel().getColumn(3).setPreferredWidth(100); // Type
        modelTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // Status
        modelTable.getColumnModel().getColumn(5).setPreferredWidth(200); // Description
        
        // Custom renderer for status column
        modelTable.getColumnModel().getColumn(4).setCellRenderer(new StatusCellRenderer());
        
        JScrollPane scrollPane = new JScrollPane(modelTable);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Bottom panel with buttons and progress
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        
        statusLabel = new JLabel(" ");
        bottomPanel.add(statusLabel, BorderLayout.NORTH);
        
        downloadProgress = new JProgressBar(0, 100);
        downloadProgress.setStringPainted(true);
        downloadProgress.setVisible(false);
        bottomPanel.add(downloadProgress, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        
        refreshButton = new JButton("Refresh List");
        refreshButton.addActionListener(e -> loadModels(true));
        buttonPanel.add(refreshButton);
        
        downloadButton = new JButton("Download Model");
        downloadButton.setEnabled(false);
        downloadButton.addActionListener(e -> downloadSelectedModel());
        buttonPanel.add(downloadButton);
        
        deleteButton = new JButton("Delete Model");
        deleteButton.setEnabled(false);
        deleteButton.addActionListener(e -> deleteSelectedModel());
        buttonPanel.add(deleteButton);
        
        useButton = new JButton("Use This Model");
        useButton.setEnabled(false);
        useButton.addActionListener(e -> useSelectedModel());
        buttonPanel.add(useButton);
        
        cancelDownloadButton = new JButton("Cancel Download");
        cancelDownloadButton.setVisible(false);
        cancelDownloadButton.addActionListener(e -> {
            DownloadManager.getInstance().cancelCurrentDownload();
            cancelDownloadButton.setVisible(false);
            statusLabel.setText("Cancelling download...");
        });
        buttonPanel.add(cancelDownloadButton);
        
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> onDialogClosing());
        buttonPanel.add(closeButton);
        
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        setContentPane(mainPanel);
        
        // Check for active downloads and restore UI state
        checkForActiveDownloads();
        
        // Load models
        loadModels(false);
    }
    
    /**
     * Check if there's an active download from a previous dialog instance.
     */
    private void checkForActiveDownloads() {
        DownloadManager dm = DownloadManager.getInstance();
        if (dm.hasActiveDownload()) {
            String modelName = dm.getActiveDownloadName();
            statusLabel.setText("Resuming download: " + modelName + "...");
            downloadProgress.setValue(0);
            downloadProgress.setVisible(true);
            setButtonsEnabled(false);
        }
    }
    
    private void loadModels(boolean forceRefresh) {
        // Check internet connectivity first
        if (!NetworkUtils.isInternetAvailable()) {
            statusLabel.setText("No internet connection - offline mode");
            loadModelsOffline();
            return;
        }
        
        statusLabel.setText("Loading models...");
        setButtonsEnabled(false);
        
        CompletableFuture.runAsync(() -> {
            try {
                List<VoskModel> models = modelManager.getAvailableModels();
                
                SwingUtilities.invokeLater(() -> {
                    tableModel.setModels(models);
                    statusLabel.setText(String.format("Found %d models (%d installed)", 
                            models.size(), 
                            models.stream().filter(VoskModel::isInstalled).count()));
                    setButtonsEnabled(true);
                });
                
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Failed to load models");
                    loadModelsOffline();
                    setButtonsEnabled(true);
                });
            }
        });
    }
    
    /**
     * Load only installed models when offline or fetch fails.
     */
    private void loadModelsOffline() {
        List<VoskModel> installedModels = modelManager.getInstalledModels();
        tableModel.setModels(installedModels);
        
        if (installedModels.isEmpty()) {
            statusLabel.setText("No models installed - internet required to download");
        } else {
            statusLabel.setText(installedModels.size() + " installed models available (offline)");
        }
    }
    
    private void checkForUpdates() {
        statusLabel.setText("Checking for updates...");
        setButtonsEnabled(false);
        
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, VoskModel> updates = modelManager.checkForUpdates();
                
                SwingUtilities.invokeLater(() -> {
                    setButtonsEnabled(true);
                    
                    if (updates.isEmpty()) {
                        statusLabel.setText("All models are up to date");
                        JOptionPane.showMessageDialog(this,
                                "All installed models are up to date!",
                                "No Updates",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        statusLabel.setText(String.format("%d update(s) available", updates.size()));
                        
                        StringBuilder message = new StringBuilder("Updates available for:\n\n");
                        updates.forEach((name, model) -> 
                                message.append("• ").append(name).append("\n"));
                        
                        int result = JOptionPane.showConfirmDialog(this,
                                message.toString() + "\nWould you like to download updates now?",
                                "Updates Available",
                                JOptionPane.YES_NO_OPTION);
                        
                        if (result == JOptionPane.YES_OPTION) {
                            // Download updates (implement batch download if needed)
                            JOptionPane.showMessageDialog(this,
                                    "Please select and download each model individually.",
                                    "Info",
                                    JOptionPane.INFORMATION_MESSAGE);
                            loadModels(true);
                        }
                    }
                });
                
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    setButtonsEnabled(true);
                    statusLabel.setText("Failed to check for updates");
                    JOptionPane.showMessageDialog(this,
                            "Failed to check for updates:\n" + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }
    
    private void downloadSelectedModel() {
        int selectedRow = modelTable.getSelectedRow();
        if (selectedRow < 0) return;
        
        // Check internet connectivity
        if (!NetworkUtils.isInternetAvailable()) {
            JOptionPane.showMessageDialog(this,
                    "No internet connection. Please connect to the internet to download models.",
                    "Offline",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        VoskModel model = tableModel.getModel(selectedRow);
        
        // Warn for big models
        if (model.isBigModel()) {
            int result = JOptionPane.showConfirmDialog(this,
                    String.format("This is a large model (%s).\nDownload may take several minutes.\n\nContinue?",
                            model.getSize()),
                    "Large Download",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        
        setButtonsEnabled(false);
        downloadProgress.setVisible(true);
        downloadProgress.setValue(0);
        cancelDownloadButton.setVisible(true);
        statusLabel.setText("Downloading " + model.getName() + "...");
        
        // Create the CompletableFuture first, then start the async task
        CompletableFuture<Void> downloadTask = new CompletableFuture<>();
        currentDownloadTask = downloadTask;
        
        // Register with DownloadManager before starting
        DownloadManager.getInstance().setActiveDownload(model.getName(), downloadTask);
        
        CompletableFuture.runAsync(() -> {
            try {
                modelManager.downloadModel(model, progress -> {
                    SwingUtilities.invokeLater(() -> {
                        downloadProgress.setValue(progress);
                        statusLabel.setText(String.format("Downloading %s... %d%%", 
                                model.getName(), progress));
                    });
                });
                
                downloadTask.complete(null); // Mark as successfully completed
                
                SwingUtilities.invokeLater(() -> {
                    downloadProgress.setVisible(false);
                    cancelDownloadButton.setVisible(false);
                    statusLabel.setText("Download complete: " + model.getName());
                    setButtonsEnabled(true);
                    loadModels(false);
                    
                    JOptionPane.showMessageDialog(this,
                            "Model downloaded and installed successfully!",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                });
                
            } catch (IOException e) {
                downloadTask.completeExceptionally(e); // Mark as failed
                
                SwingUtilities.invokeLater(() -> {
                    downloadProgress.setVisible(false);
                    cancelDownloadButton.setVisible(false);
                    
                    // Check if it was user cancellation
                    if (e.getMessage() != null && e.getMessage().contains("cancelled")) {
                        statusLabel.setText("Download cancelled");
                    } else {
                        statusLabel.setText("Download failed");
                        JOptionPane.showMessageDialog(this,
                                "Failed to download model:\n" + e.getMessage(),
                                "Download Error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                    
                    setButtonsEnabled(true);
                });
            }
        });
    }
    
    private void deleteSelectedModel() {
        int selectedRow = modelTable.getSelectedRow();
        if (selectedRow < 0) return;
        
        VoskModel model = tableModel.getModel(selectedRow);
        
        int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete this model?\n" + model.getName(),
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        
        try {
            modelManager.deleteModel(model.getName());
            statusLabel.setText("Deleted: " + model.getName());
            loadModels(false);
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to delete model:\n" + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void useSelectedModel() {
        int selectedRow = modelTable.getSelectedRow();
        if (selectedRow < 0) return;
        
        selectedModel = tableModel.getModel(selectedRow);
        onDialogClosing();
    }
    
    /**
     * Handle dialog closing - check if download is in progress.
     */
    private void onDialogClosing() {
        // If a download is in progress, ask user if they want to cancel
        if (DownloadManager.getInstance().hasActiveDownload()) {
            int result = JOptionPane.showConfirmDialog(this,
                    "A download is in progress. Cancel it and close?\n" +
                    "(Cancelling will remove the incomplete file)",
                    "Download In Progress",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            
            if (result == JOptionPane.YES_OPTION) {
                DownloadManager.getInstance().cancelCurrentDownload();
            } else {
                return; // Don't close
            }
        }
        
        dispose();
    }
    
    private void updateButtonStates() {
        int selectedRow = modelTable.getSelectedRow();
        
        if (selectedRow >= 0) {
            VoskModel model = tableModel.getModel(selectedRow);
            downloadButton.setEnabled(!model.isInstalled());
            deleteButton.setEnabled(model.isInstalled());
            useButton.setEnabled(model.isInstalled());
        } else {
            downloadButton.setEnabled(false);
            deleteButton.setEnabled(false);
            useButton.setEnabled(false);
        }
    }
    
    private void setButtonsEnabled(boolean enabled) {
        refreshButton.setEnabled(enabled);
        if (enabled) {
            updateButtonStates();
        } else {
            downloadButton.setEnabled(false);
            deleteButton.setEnabled(false);
            useButton.setEnabled(false);
        }
    }
    
    public VoskModel getSelectedModel() {
        return selectedModel;
    }
    
    // Table Model
    private static class ModelTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Name", "Language", "Size", "Type", "Status", "Description"};
        private List<VoskModel> models = new ArrayList<>();
        
        public void setModels(List<VoskModel> models) {
            this.models = new ArrayList<>(models);
            fireTableDataChanged();
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
                case 4 -> model.isInstalled() ? "Installed" : "Available";
                case 5 -> model.getDescription();
                default -> "";
            };
        }
    }
    
    // Status cell renderer
    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            if (value != null && value.toString().equals("Installed")) {
                if (!isSelected) {
                    c.setBackground(new Color(200, 255, 200));
                }
                setForeground(new Color(0, 128, 0));
                setFont(getFont().deriveFont(Font.BOLD));
            } else {
                if (!isSelected) {
                    c.setBackground(table.getBackground());
                }
                setForeground(table.getForeground());
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            
            return c;
        }
    }
}
