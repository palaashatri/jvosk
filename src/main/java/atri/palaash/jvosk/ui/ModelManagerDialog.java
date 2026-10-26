package atri.palaash.jvosk.ui;

import atri.palaash.jvosk.models.DownloadManager;
import atri.palaash.jvosk.models.DownloadQueue;
import atri.palaash.jvosk.models.ModelManager;
import atri.palaash.jvosk.models.VoskModel;
import atri.palaash.jvosk.util.AppPreferences;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Dialog for managing Vosk models - viewing, downloading, and deleting models.
 */
public class ModelManagerDialog extends JDialog {
    
    private final ModelManager modelManager;
    private final DownloadQueue downloadQueue;
    private final JTable modelTable;
    private final ModelTableModel tableModel;
    private final JButton checkUpdatesButton;
    private final JButton downloadButton;
    private final JButton queueDownloadsButton;
    private final JButton deleteButton;
    private final JButton useButton;
    private final JButton refreshButton;
    private final JButton cancelDownloadButton;
    private final JProgressBar downloadProgress;
    private final JLabel statusLabel;
    private VoskModel selectedModel = null;
    private Set<String> updatesAvailable = Set.of();
    private Set<String> metadataMissing = Set.of();
    private volatile boolean queueStopRequested;
    private volatile boolean queueRunning;
    private boolean controlsEnabled = true;
    private boolean resumePromptShown;
    
    public ModelManagerDialog(Frame owner, ModelManager modelManager) {
        super(owner, "Model Manager", true);
        this.modelManager = modelManager;
        this.downloadQueue = new DownloadQueue(modelManager.getModelsDirectory());
        
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
        
        checkUpdatesButton = new JButton("Check for Updates");
        checkUpdatesButton.addActionListener(e -> checkForUpdates());
        headerPanel.add(checkUpdatesButton, BorderLayout.EAST);
        
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        // Table
        tableModel = new ModelTableModel();
        modelTable = new JTable(tableModel);
        modelTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
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
        modelTable.getColumnModel().getColumn(4).setPreferredWidth(150); // Status
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

        queueDownloadsButton = new JButton("Queue Selected");
        queueDownloadsButton.setEnabled(false);
        queueDownloadsButton.addActionListener(e -> queueSelectedDownloads());
        buttonPanel.add(queueDownloadsButton);
        
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
            queueStopRequested = true;
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
        statusLabel.setText("Loading models...");
        setButtonsEnabled(false);

        CompletableFuture.runAsync(() -> {
            try {
                List<VoskModel> models = modelManager.getAvailableModels(forceRefresh);
                Map<String, String> adopted;
                try {
                    adopted = modelManager.migrateCatalogueRenames(models);
                    if (!adopted.isEmpty()) {
                        AppPreferences.remapSelectedModel(modelManager.getModelsDirectory(), adopted);
                    }
                } catch (IOException e) {
                    System.err.println("Failed to adopt catalogue renames: " + e.getMessage());
                    adopted = Map.of();
                }
                if (!adopted.isEmpty()) {
                    models = modelManager.getAvailableModels(false);
                }
                Map<String, Long> partials = modelManager.getPartialDownloads();
                List<VoskModel> loadedModels = models;
                String adoptedNote = adopted.isEmpty()
                        ? ""
                        : String.format(" - adopted %d catalogue rename(s)", adopted.size());

                SwingUtilities.invokeLater(() -> {
                    tableModel.setModels(loadedModels, partials, catalogueNames(loadedModels));
                    boolean bundled = modelManager.isUsingFallbackCatalogue();
                    statusLabel.setText(String.format("Found %d models (%d installed)%s%s",
                            loadedModels.size(),
                            loadedModels.stream().filter(VoskModel::isInstalled).count(),
                            bundled ? " - bundled catalogue (offline)" : "",
                            adoptedNote));
                    setButtonsEnabled(true);
                    maybeResumePersistedQueue(loadedModels);
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

    private static Set<String> catalogueNames(List<VoskModel> models) {
        return models.stream()
                .filter(ModelDownloadActions::hasDownloadSource)
                .map(VoskModel::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    
    /**
     * Load only installed models when offline or fetch fails.
     */
    private void loadModelsOffline() {
        List<VoskModel> installedModels = modelManager.getInstalledModels();
        tableModel.setModels(installedModels, Map.of());
        
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
                Set<String> catalogueNames = catalogueNames(modelManager.getAvailableModels(false));
                Set<String> unverified = modelManager.getInstalledModelsWithoutUpdateMetadata().stream()
                        .map(VoskModel::getName)
                        .filter(catalogueNames::contains)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                List<String> orphaned = modelManager.getInstalledModels().stream()
                        .map(VoskModel::getName)
                        .filter(name -> !catalogueNames.contains(name))
                        .sorted()
                        .toList();

                SwingUtilities.invokeLater(() -> {
                    updatesAvailable = Set.copyOf(updates.keySet());
                    metadataMissing = unverified;
                    setButtonsEnabled(true);

                    if (updates.isEmpty() && unverified.isEmpty() && orphaned.isEmpty()) {
                        statusLabel.setText("All models are up to date");
                        JOptionPane.showMessageDialog(this,
                                "All installed models are up to date!",
                                "No Updates",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        String updateLabel = updates.size() == 1 ? "update" : "updates";
                        String installationLabel = unverified.size() == 1 ? "installation" : "installations";
                        String verificationVerb = unverified.size() == 1 ? "needs" : "need";
                        String orphanLabel = orphaned.size() == 1 ? "install" : "installs";
                        statusLabel.setText(String.format("%d %s; %d %s %s verification; %d %s not in catalogue",
                                updates.size(), updateLabel,
                                unverified.size(), installationLabel, verificationVerb,
                                orphaned.size(), orphanLabel));

                        StringBuilder message = new StringBuilder();
                        if (!updates.isEmpty()) {
                            message.append("Updates available for:\n\n");
                            updates.keySet().forEach(name -> message.append("- ").append(name).append("\n"));
                        }
                        if (!unverified.isEmpty()) {
                            message.append("\nThese older installations have no saved checksum/version and cannot be checked:\n");
                            unverified.forEach(name -> message.append("- ").append(name).append("\n"));
                            message.append("Reinstall them once to enable reliable update checks.\n");
                        }
                        if (!orphaned.isEmpty()) {
                            message.append("\nThese installed models are no longer in the catalogue and cannot be updated:\n");
                            orphaned.forEach(name -> message.append("- ").append(name).append("\n"));
                            message.append("Delete them or choose a current catalogue model to install.\n");
                        }

                        if (!updates.isEmpty() || !unverified.isEmpty()) {
                            int result = JOptionPane.showConfirmDialog(this,
                                    message + "\nWould you like to update/reinstall these models now?",
                                    "Model Verification",
                                    JOptionPane.YES_NO_OPTION);

                            if (result == JOptionPane.YES_OPTION) {
                                JOptionPane.showMessageDialog(this,
                                        "Select a listed model and choose Update/Reinstall, or add it to the queue.",
                                        "Info",
                                        JOptionPane.INFORMATION_MESSAGE);
                            }
                        } else {
                            JOptionPane.showMessageDialog(this,
                                    message.toString(),
                                    "Catalogue Status",
                                    JOptionPane.INFORMATION_MESSAGE);
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
        int[] selectedRows = modelTable.getSelectedRows();
        if (selectedRows.length != 1) {
            return;
        }
        queueModels(List.of(tableModel.getModel(selectedRows[0])));
    }

    private void queueSelectedDownloads() {
        List<VoskModel> selected = new ArrayList<>();
        for (int row : modelTable.getSelectedRows()) {
            VoskModel model = tableModel.getModel(row);
            if (isDownloadCandidate(model)) {
                selected.add(model);
            }
        }
        queueModels(selected);
    }

    private boolean isDownloadCandidate(VoskModel model) {
        return ModelDownloadActions.isDownloadCandidate(model, updatesAvailable, metadataMissing);
    }

    private void maybeResumePersistedQueue(List<VoskModel> models) {
        List<String> pending = downloadQueue.remaining();
        if (pending.isEmpty() || queueRunning || resumePromptShown) {
            return;
        }
        resumePromptShown = true;
        List<VoskModel> resume = ModelDownloadActions.resumableModels(
                pending, models, updatesAvailable, metadataMissing);
        if (resume.isEmpty()) {
            try {
                downloadQueue.clear();
            } catch (IOException e) {
                System.err.println("Failed to clear stale download queue: " + e.getMessage());
            }
            return;
        }
        int result = JOptionPane.showConfirmDialog(this,
                String.format("%d queued download(s) were left unfinished.%nResume them now?", resume.size()),
                "Resume Download Queue",
                JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            queueModels(resume);
        } else if (result == JOptionPane.NO_OPTION) {
            try {
                downloadQueue.clear();
            } catch (IOException e) {
                System.err.println("Failed to discard declined download queue: " + e.getMessage());
            }
        }
    }

    private void queueModels(List<VoskModel> requestedModels) {
        List<VoskModel> models = requestedModels.stream()
                .filter(this::isDownloadCandidate)
                .distinct()
                .toList();
        if (models.isEmpty() || queueRunning) {
            return;
        }

        long largeModels = models.stream().filter(VoskModel::isBigModel).count();
        if (largeModels > 0) {
            int result = JOptionPane.showConfirmDialog(this,
                    String.format("The queue contains %d large model(s).\nDownloads may take several minutes.\n\nContinue?",
                            largeModels),
                    "Large Downloads",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        queueStopRequested = false;
        queueRunning = true;
        setButtonsEnabled(false);
        downloadProgress.setVisible(true);
        downloadProgress.setValue(0);
        cancelDownloadButton.setVisible(true);
        try {
            downloadQueue.setPending(models.stream().map(VoskModel::getName).toList());
        } catch (IOException e) {
            statusLabel.setText("Could not persist download queue: " + e.getMessage());
        }

        CompletableFuture.runAsync(() -> {
            List<String> failures = new ArrayList<>();
            int completed = 0;
            String currentName = "";

            for (VoskModel model : models) {
                if (queueStopRequested) {
                    break;
                }
                currentName = model.getName();
                String downloadingName = currentName;
                SwingUtilities.invokeLater(() -> {
                    downloadProgress.setValue(0);
                    statusLabel.setText("Downloading " + downloadingName + "...");
                });

                CompletableFuture<Void> activeDownload = new CompletableFuture<>();
                DownloadManager.getInstance().setActiveDownload(currentName, activeDownload);
                try {
                    modelManager.downloadModel(model, progress -> SwingUtilities.invokeLater(() -> {
                        downloadProgress.setValue(progress);
                        statusLabel.setText(String.format("Downloading %s... %d%%",
                                downloadingName, progress));
                    }));
                    activeDownload.complete(null);
                    completed++;
                    try {
                        downloadQueue.markCompleted(downloadingName);
                    } catch (IOException persistError) {
                        System.err.println("Failed to persist queue progress: " + persistError.getMessage());
                    }
                    SwingUtilities.invokeLater(() -> removeAvailableUpdate(downloadingName));
                } catch (IOException | RuntimeException e) {
                    activeDownload.completeExceptionally(e);
                    failures.add(currentName + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                    if (queueStopRequested || (e.getMessage() != null
                            && e.getMessage().toLowerCase(java.util.Locale.ROOT).contains("cancelled"))) {
                        break;
                    }
                } finally {
                    DownloadManager.getInstance().clearActiveDownload();
                }
            }

            int completedCount = completed;
            List<String> failureCopy = List.copyOf(failures);
            boolean stopped = queueStopRequested;
            SwingUtilities.invokeLater(() -> finishDownloadQueue(models.size(), completedCount, failureCopy, stopped));
        }).whenComplete((ignored, error) -> DownloadManager.getInstance().clearActiveDownload());
    }

    private void removeAvailableUpdate(String modelName) {
        if (updatesAvailable.contains(modelName)) {
            Set<String> remainingUpdates = new java.util.HashSet<>(updatesAvailable);
            remainingUpdates.remove(modelName);
            updatesAvailable = Set.copyOf(remainingUpdates);
        }
        if (metadataMissing.contains(modelName)) {
            Set<String> remainingUnverified = new java.util.HashSet<>(metadataMissing);
            remainingUnverified.remove(modelName);
            metadataMissing = Set.copyOf(remainingUnverified);
        }
    }

    private void finishDownloadQueue(int requested, int completed, List<String> failures, boolean stopped) {
        queueRunning = false;
        queueStopRequested = false;
        if (!stopped && downloadQueue.isEmpty()) {
            try {
                downloadQueue.clear();
            } catch (IOException e) {
                System.err.println("Failed to clear finished download queue: " + e.getMessage());
            }
        }
        if (!isDisplayable()) {
            return;
        }
        downloadProgress.setVisible(false);
        cancelDownloadButton.setVisible(false);
        setButtonsEnabled(true);

        String message = String.format("Download queue finished: %d of %d complete", completed, requested);
        if (stopped) {
            message += " (cancelled; partial downloads can be resumed)";
        }
        if (!failures.isEmpty()) {
            message += String.format("; %d failed", failures.size());
        }
        String completedMessage = message;
        statusLabel.setText(message);

        // Refresh catalogue and partial byte counts off the EDT after the queue.
        CompletableFuture.runAsync(() -> {
            try {
                List<VoskModel> refreshedModels = modelManager.getAvailableModels(false);
                Map<String, Long> partials = modelManager.getPartialDownloads();
                SwingUtilities.invokeLater(() -> {
                    tableModel.setModels(refreshedModels, partials);
                    setButtonsEnabled(true);
                    statusLabel.setText(completedMessage);
                    if (!failures.isEmpty()) {
                        JOptionPane.showMessageDialog(this,
                                String.join("\n", failures), "Some Downloads Failed", JOptionPane.WARNING_MESSAGE);
                    }
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    setButtonsEnabled(true);
                    statusLabel.setText(completedMessage + "; catalogue refresh failed");
                    JOptionPane.showMessageDialog(this,
                            "Failed to refresh the model list:\n" + e.getMessage(),
                            "Refresh Error", JOptionPane.WARNING_MESSAGE);
                });
            }
        });
    }
    
    private void deleteSelectedModel() {
        int[] selectedRows = modelTable.getSelectedRows();
        if (selectedRows.length != 1) return;
        int selectedRow = selectedRows[0];
        
        VoskModel model = tableModel.getModel(selectedRow);
        String modelName = model.getName();
        
        int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete this model?\n" + modelName,
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        setButtonsEnabled(false);
        statusLabel.setText("Deleting " + modelName + "...");

        // Deleting a multi-gigabyte model can take a while; keep it off the EDT.
        CompletableFuture.runAsync(() -> {
            try {
                modelManager.deleteModel(modelName);
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Deleted: " + modelName);
                    setButtonsEnabled(true);
                    loadModels(false);
                });
            } catch (IOException | RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    setButtonsEnabled(true);
                    statusLabel.setText("Failed to delete model");
                    JOptionPane.showMessageDialog(this,
                            "Failed to delete model:\n" + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }
    
    private void useSelectedModel() {
        int[] selectedRows = modelTable.getSelectedRows();
        if (selectedRows.length != 1) return;
        int selectedRow = selectedRows[0];
        
        selectedModel = tableModel.getModel(selectedRow);
        onDialogClosing();
    }
    
    /**
     * Handle dialog closing - check if download is in progress.
     */
    private void onDialogClosing() {
        // If a download is in progress, ask user if they want to cancel
        if (queueRunning || DownloadManager.getInstance().hasActiveDownload()) {
            int result = JOptionPane.showConfirmDialog(this,
                    "A download queue is active. Cancel it and close?\n" +
                    "(The current partial download can be resumed later)",
                    "Download In Progress",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            
            if (result == JOptionPane.YES_OPTION) {
                queueStopRequested = true;
                DownloadManager.getInstance().cancelCurrentDownload();
            } else {
                return; // Don't close
            }
        }
        
        dispose();
    }
    
    private void updateButtonStates() {
        if (!controlsEnabled || queueRunning) {
            downloadButton.setEnabled(false);
            queueDownloadsButton.setEnabled(false);
            deleteButton.setEnabled(false);
            useButton.setEnabled(false);
            return;
        }

        int[] selectedRows = modelTable.getSelectedRows();
        boolean singleSelection = selectedRows.length == 1;

        if (singleSelection) {
            VoskModel model = tableModel.getModel(selectedRows[0]);
            boolean partial = tableModel.hasPartialDownload(model.getName());
            downloadButton.setText(ModelDownloadActions.downloadButtonText(
                    model, updatesAvailable, metadataMissing, partial));
            downloadButton.setEnabled(isDownloadCandidate(model));
            deleteButton.setEnabled(model.isInstalled());
            useButton.setEnabled(model.isInstalled());
        } else {
            downloadButton.setText("Download Model");
            downloadButton.setEnabled(false);
            deleteButton.setEnabled(false);
            useButton.setEnabled(false);
        }

        queueDownloadsButton.setEnabled(java.util.Arrays.stream(selectedRows)
                .mapToObj(tableModel::getModel)
                .anyMatch(this::isDownloadCandidate));
    }
    
    private void setButtonsEnabled(boolean enabled) {
        controlsEnabled = enabled;
        checkUpdatesButton.setEnabled(enabled);
        refreshButton.setEnabled(enabled);
        if (enabled) {
            updateButtonStates();
        } else {
            downloadButton.setEnabled(false);
            queueDownloadsButton.setEnabled(false);
            deleteButton.setEnabled(false);
            useButton.setEnabled(false);
        }
    }
    
    public VoskModel getSelectedModel() {
        return selectedModel;
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
