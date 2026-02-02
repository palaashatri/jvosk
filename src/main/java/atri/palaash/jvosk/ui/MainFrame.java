package atri.palaash.jvosk.ui;

import atri.palaash.jvosk.stt.VoskTranscriber;
import atri.palaash.jvosk.util.AppPreferences;
import atri.palaash.jvosk.util.AudioInfo;
import atri.palaash.jvosk.util.TranscriptExporter;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;

public class MainFrame extends JFrame {

    private final JTextArea transcriptArea;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final JLabel statsLabel;
    private final JLabel audioInfoLabel;
    private final JPanel dropPanel;
    
    private VoskTranscriber transcriber;
    private SwingWorker<Void, String> currentWorker;
    private File currentAudioFile;
    private long transcriptionStartTime;
    private boolean hasUnsavedChanges = false;
    
    // UI Components for actions
    private JButton copyButton;
    private JButton saveButton;
    private JButton clearButton;
    private JCheckBoxMenuItem timestampMenuItem;
    private JCheckBoxMenuItem darkModeMenuItem;

    public MainFrame() {
        super("jvosk – Speech-to-Text");

        // Initialize transcriber with saved model preference
        initializeTranscriber();

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                handleExit();
            }
        });
        
        setSize(900, 700);
        setLocationRelativeTo(null);

        // Create menu bar
        setJMenuBar(createMenuBar());

        // Main content panel
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(content);

        // Center: Drop zone + transcript
        JPanel centerPanel = new JPanel(new BorderLayout(8, 8));
        
        // Drop zone with better visuals
        dropPanel = createDropPanel();
        centerPanel.add(dropPanel, BorderLayout.NORTH);

        // Transcript area
        transcriptArea = new JTextArea();
        transcriptArea.setEditable(false);
        transcriptArea.setLineWrap(true);
        transcriptArea.setWrapStyleWord(true);
        transcriptArea.setFont(new Font("Monospaced", Font.PLAIN, AppPreferences.getFontSize()));
        transcriptArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateStats(); hasUnsavedChanges = true; }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateStats(); hasUnsavedChanges = true; }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateStats(); }
        });

        JScrollPane scrollPane = new JScrollPane(transcriptArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Transcript"));
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        content.add(centerPanel, BorderLayout.CENTER);

        // Bottom: Status + Progress + Stats + Buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        
        audioInfoLabel = new JLabel("No file selected");
        audioInfoLabel.setBorder(new EmptyBorder(2, 0, 2, 0));
        bottomPanel.add(audioInfoLabel, BorderLayout.NORTH);
        
        statusLabel = new JLabel("Ready");
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        
        JPanel statusPanel = new JPanel(new BorderLayout(8, 0));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);
        bottomPanel.add(statusPanel, BorderLayout.CENTER);
        
        statsLabel = new JLabel("Words: 0 | Characters: 0");
        statsLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        JPanel statsPanel = new JPanel(new BorderLayout());
        statsPanel.add(statsLabel, BorderLayout.WEST);
        
        // Action buttons panel
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        
        copyButton = new JButton("Copy");
        copyButton.setToolTipText("Copy to clipboard");
        copyButton.addActionListener(e -> copyToClipboard());
        styleButton(copyButton, new Color(52, 152, 219));
        actionPanel.add(copyButton);
        
        saveButton = new JButton("Save");
        saveButton.setToolTipText("Save transcript (Cmd/Ctrl+S)");
        saveButton.addActionListener(e -> saveTranscript());
        styleButton(saveButton, new Color(46, 204, 113));
        actionPanel.add(saveButton);
        
        clearButton = new JButton("Clear");
        clearButton.setToolTipText("Clear transcript (Cmd/Ctrl+N)");
        clearButton.addActionListener(e -> clearTranscript());
        styleButton(clearButton, new Color(231, 76, 60));
        actionPanel.add(clearButton);
        
        statsPanel.add(actionPanel, BorderLayout.EAST);
        bottomPanel.add(statsPanel, BorderLayout.SOUTH);

        content.add(bottomPanel, BorderLayout.SOUTH);

        // Setup keyboard shortcuts
        setupKeyboardShortcuts();
        
        // Apply dark mode if saved
        if (AppPreferences.isDarkMode()) {
            setDarkMode(true);
        }
        
        updateButtonStates();
    }
    
    private void initializeTranscriber() {
        String modelPath = AppPreferences.getSelectedModel();
        try {
            this.transcriber = new VoskTranscriber(modelPath);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Failed to load model: " + modelPath + "\n" + e.getMessage(),
                "Model Error",
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File Menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        
        JMenuItem openItem = new JMenuItem("Open Audio File...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        openItem.addActionListener(e -> browseForFile());
        fileMenu.add(openItem);
        
        JMenu recentMenu = new JMenu("Recent Files");
        updateRecentFilesMenu(recentMenu);
        fileMenu.add(recentMenu);
        
        fileMenu.addSeparator();
        
        JMenuItem saveItem = new JMenuItem("Save Transcript...");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        saveItem.addActionListener(e -> saveTranscript());
        fileMenu.add(saveItem);
        
        JMenu exportMenu = new JMenu("Export As");
        JMenuItem exportSRT = new JMenuItem("Subtitle (SRT)");
        exportSRT.addActionListener(e -> exportAs("srt"));
        JMenuItem exportVTT = new JMenuItem("WebVTT");
        exportVTT.addActionListener(e -> exportAs("vtt"));
        JMenuItem exportJSON = new JMenuItem("JSON");
        exportJSON.addActionListener(e -> exportAs("json"));
        JMenuItem exportMD = new JMenuItem("Markdown");
        exportMD.addActionListener(e -> exportAs("md"));
        exportMenu.add(exportSRT);
        exportMenu.add(exportVTT);
        exportMenu.add(exportJSON);
        exportMenu.add(exportMD);
        fileMenu.add(exportMenu);
        
        fileMenu.addSeparator();
        
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        exitItem.addActionListener(e -> handleExit());
        fileMenu.add(exitItem);
        
        menuBar.add(fileMenu);

        // Edit Menu
        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic(KeyEvent.VK_E);
        
        JMenuItem copyItem = new JMenuItem("Copy Transcript");
        copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK));
        copyItem.addActionListener(e -> copyToClipboard());
        editMenu.add(copyItem);
        
        JMenuItem clearItem = new JMenuItem("Clear Transcript");
        clearItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        clearItem.addActionListener(e -> clearTranscript());
        editMenu.add(clearItem);
        
        menuBar.add(editMenu);

        // View Menu
        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);
        
        darkModeMenuItem = new JCheckBoxMenuItem("Dark Mode");
        darkModeMenuItem.setSelected(AppPreferences.isDarkMode());
        darkModeMenuItem.addActionListener(e -> toggleDarkMode());
        viewMenu.add(darkModeMenuItem);
        
        timestampMenuItem = new JCheckBoxMenuItem("Show Timestamps");
        timestampMenuItem.setSelected(AppPreferences.isShowTimestamps());
        timestampMenuItem.addActionListener(e -> toggleTimestamps());
        viewMenu.add(timestampMenuItem);
        
        viewMenu.addSeparator();
        
        JMenuItem increaseFontItem = new JMenuItem("Increase Font Size");
        increaseFontItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        increaseFontItem.addActionListener(e -> changeFontSize(2));
        viewMenu.add(increaseFontItem);
        
        JMenuItem decreaseFontItem = new JMenuItem("Decrease Font Size");
        decreaseFontItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        decreaseFontItem.addActionListener(e -> changeFontSize(-2));
        viewMenu.add(decreaseFontItem);
        
        menuBar.add(viewMenu);

        return menuBar;
    }

    private JPanel createDropPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Select or drag & drop audio file"));
        panel.setPreferredSize(new Dimension(200, 100));
        
        JLabel dropLabel = new JLabel("<html><center>Drag & drop an audio file<br><small>WAV, MP3, M4A, FLAC, OGG, AAC, WMA, OPUS</small></center></html>", SwingConstants.CENTER);
        panel.add(dropLabel, BorderLayout.CENTER);
        
        JButton browseButton = new JButton("Browse Files...");
        browseButton.addActionListener(e -> browseForFile());
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(browseButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        panel.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    panel.setBackground(new Color(200, 230, 200));
                    return true;
                }
                return false;
            }

            @Override
            public boolean importData(TransferSupport support) {
                panel.setBackground(null);
                try {
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        startTranscription(files.get(0));
                    }
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    setStatus("Error: " + e.getMessage());
                    return false;
                }
            }
        });

        return panel;
    }

    private void setupKeyboardShortcuts() {
        JRootPane rootPane = getRootPane();
        int modifier = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        
        rootPane.registerKeyboardAction(e -> browseForFile(),
            KeyStroke.getKeyStroke(KeyEvent.VK_O, modifier),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        
        rootPane.registerKeyboardAction(e -> saveTranscript(),
            KeyStroke.getKeyStroke(KeyEvent.VK_S, modifier),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        
        rootPane.registerKeyboardAction(e -> clearTranscript(),
            KeyStroke.getKeyStroke(KeyEvent.VK_N, modifier),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        
        rootPane.registerKeyboardAction(e -> copyToClipboard(),
            KeyStroke.getKeyStroke(KeyEvent.VK_C, modifier | KeyEvent.SHIFT_DOWN_MASK),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void browseForFile() {
        JFileChooser fileChooser = new JFileChooser(AppPreferences.getLastOpenDir());
        fileChooser.setDialogTitle("Select Audio File");
        fileChooser.setFileFilter(new FileNameExtensionFilter(
            "Audio Files (*.wav, *.mp3, *.m4a, *.flac, *.ogg, *.aac, *.wma, *.opus)",
            "wav", "mp3", "m4a", "flac", "ogg", "aac", "wma", "opus"));
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = fileChooser.getSelectedFile();
            AppPreferences.setLastOpenDir(selected.getParent());
            startTranscription(selected);
        }
    }

    private void startTranscription(File audioFile) {
        if (currentWorker != null && !currentWorker.isDone()) {
            JOptionPane.showMessageDialog(this,
                "A transcription is already in progress. Please wait or cancel it first.",
                "Busy",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        currentAudioFile = audioFile;
        AppPreferences.addRecentFile(audioFile.getAbsolutePath());
        updateRecentFilesMenu(null);
        
        transcriptArea.setText("");
        hasUnsavedChanges = false;
        
        // Display audio info
        AudioInfo info = AudioInfo.from(audioFile);
        audioInfoLabel.setText(String.format("%s | Duration: %s | Est. time: ~%ds",
            info.toString(),
            info.getFormattedDuration(),
            info.getEstimatedTranscriptionSeconds()));
        
        setStatus("Processing: " + audioFile.getName());
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Converting audio...");
        
        transcriptionStartTime = System.currentTimeMillis();
        updateButtonStates();

        currentWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                transcriber.transcribeFile(audioFile, partial -> {
                    // Check if cancellation was requested
                    if (isCancelled() || Thread.currentThread().isInterrupted()) {
                        return; // Stop processing
                    }
                    
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setIndeterminate(false);
                        progressBar.setString("Transcribing...");
                    });
                    
                    String text = partial;
                    if (AppPreferences.isShowTimestamps()) {
                        long elapsed = System.currentTimeMillis() - transcriptionStartTime;
                        text = String.format("[%s] %s", formatTimestamp(elapsed), partial);
                    }
                    publish(text);
                });
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    transcriptArea.append(chunk + "\n");
                }
                int progress = Math.min(90, transcriptArea.getText().length() / 10);
                progressBar.setValue(progress);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                
                if (isCancelled()) {
                    setStatus("Cancelled by user");
                } else {
                    try {
                        get();
                        progressBar.setValue(100);
                        progressBar.setString("Complete");
                        long elapsed = System.currentTimeMillis() - transcriptionStartTime;
                        setStatus(String.format("Done in %d seconds", elapsed / 1000));
                    } catch (java.util.concurrent.CancellationException e) {
                        setStatus("Cancelled by user");
                    } catch (Exception e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        String errorMsg = cause.getMessage();
                        
                        // Don't show error if it was a cancellation
                        if (cause instanceof InterruptedException) {
                            setStatus("Cancelled by user");
                        } else {
                            setStatus("Error: " + errorMsg);
                            transcriptArea.setText("Transcription failed:\n" + errorMsg);
                            e.printStackTrace();
                        }
                    }
                }
                
                currentWorker = null;
                updateButtonStates();
            }
        };

        currentWorker.execute();
    }

    private void copyToClipboard() {
        String text = transcriptArea.getText();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No text to copy",
                "Info",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        StringSelection selection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, null);
        
        setStatus("Copied to clipboard");
    }

    private void saveTranscript() {
        String text = transcriptArea.getText();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No transcript to save",
                "Info",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser(AppPreferences.getLastSaveDir());
        fileChooser.setDialogTitle("Save Transcript");
        fileChooser.setSelectedFile(new File(getSuggestedFilename() + ".txt"));
        fileChooser.setFileFilter(new FileNameExtensionFilter("Text Files (*.txt)", "txt"));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            AppPreferences.setLastSaveDir(file.getParent());
            
            try {
                TranscriptExporter.exportAsText(text, file);
                hasUnsavedChanges = false;
                setStatus("Saved: " + file.getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to save file:\n" + e.getMessage(),
                    "Save Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportAs(String format) {
        String text = transcriptArea.getText();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No transcript to export",
                "Info",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        String filterDesc = "";
        String ext = "";
        switch (format) {
            case "srt": filterDesc = "SubRip Subtitle (*.srt)"; ext = "srt"; break;
            case "vtt": filterDesc = "WebVTT (*.vtt)"; ext = "vtt"; break;
            case "json": filterDesc = "JSON (*.json)"; ext = "json"; break;
            case "md": filterDesc = "Markdown (*.md)"; ext = "md"; break;
        }
        
        JFileChooser fileChooser = new JFileChooser(AppPreferences.getLastSaveDir());
        fileChooser.setDialogTitle("Export As " + ext.toUpperCase());
        fileChooser.setSelectedFile(new File(getSuggestedFilename() + "." + ext));
        fileChooser.setFileFilter(new FileNameExtensionFilter(filterDesc, ext));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            AppPreferences.setLastSaveDir(file.getParent());
            
            try {
                if (format.equals("md")) {
                    TranscriptExporter.exportAsMarkdown(text, file);
                } else {
                    // For formats requiring segments, create simple segments from lines
                    JOptionPane.showMessageDialog(this,
                        "Timed exports (SRT/VTT/JSON) require timestamp data.\nSaving as plain text in this format.",
                        "Info",
                        JOptionPane.INFORMATION_MESSAGE);
                    TranscriptExporter.exportAsText(text, file);
                }
                setStatus("Exported: " + file.getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to export file:\n" + e.getMessage(),
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void clearTranscript() {
        if (!transcriptArea.getText().isEmpty() && hasUnsavedChanges) {
            int result = JOptionPane.showConfirmDialog(this,
                "You have unsaved changes. Clear anyway?",
                "Confirm Clear",
                JOptionPane.YES_NO_OPTION);
            
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        
        transcriptArea.setText("");
        audioInfoLabel.setText("No file selected");
        currentAudioFile = null;
        hasUnsavedChanges = false;
        setStatus("Ready");
        updateButtonStates();
    }

    private void toggleDarkMode() {
        boolean isDark = darkModeMenuItem.isSelected();
        setDarkMode(isDark);
        AppPreferences.setDarkMode(isDark);
    }

    private void setDarkMode(boolean dark) {
        try {
            if (dark) {
                UIManager.setLookAndFeel(new com.formdev.flatlaf.themes.FlatMacDarkLaf());
            } else {
                UIManager.setLookAndFeel(new com.formdev.flatlaf.themes.FlatMacLightLaf());
            }
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void toggleTimestamps() {
        boolean show = timestampMenuItem.isSelected();
        AppPreferences.setShowTimestamps(show);
        setStatus(show ? "Timestamps enabled for next transcription" : "Timestamps disabled");
    }

    private void changeFontSize(int delta) {
        int currentSize = AppPreferences.getFontSize();
        int newSize = Math.max(8, Math.min(32, currentSize + delta));
        AppPreferences.setFontSize(newSize);
        transcriptArea.setFont(new Font("Monospaced", Font.PLAIN, newSize));
    }

    private void updateStats() {
        String text = transcriptArea.getText();
        int chars = text.length();
        int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        
        String stats = String.format("Words: %d | Characters: %d", words, chars);
        
        if (currentAudioFile != null) {
            AudioInfo info = AudioInfo.from(currentAudioFile);
            long duration = info.getDurationSeconds();
            if (duration > 0 && words > 0) {
                double wpm = (words / (duration / 60.0));
                stats += String.format(" | WPM: %.1f", wpm);
            }
        }
        
        statsLabel.setText(stats);
    }

    private void updateButtonStates() {
        boolean hasText = !transcriptArea.getText().isEmpty();
        
        copyButton.setEnabled(hasText);
        saveButton.setEnabled(hasText);
        clearButton.setEnabled(hasText);
    }

    private void updateRecentFilesMenu(JMenu recentMenu) {
        if (recentMenu == null) {
            // Find and update existing menu
            JMenuBar menuBar = getJMenuBar();
            if (menuBar != null) {
                JMenu fileMenu = menuBar.getMenu(0);
                for (int i = 0; i < fileMenu.getItemCount(); i++) {
                    if (fileMenu.getItem(i) instanceof JMenu) {
                        JMenu menu = (JMenu) fileMenu.getItem(i);
                        if (menu.getText().equals("Recent Files")) {
                            recentMenu = menu;
                            break;
                        }
                    }
                }
            }
        }
        
        if (recentMenu != null) {
            recentMenu.removeAll();
            List<String> recent = AppPreferences.getRecentFiles();
            
            if (recent.isEmpty()) {
                JMenuItem emptyItem = new JMenuItem("(No recent files)");
                emptyItem.setEnabled(false);
                recentMenu.add(emptyItem);
            } else {
                for (String path : recent) {
                    File f = new File(path);
                    JMenuItem item = new JMenuItem(f.getName());
                    item.setToolTipText(path);
                    item.addActionListener(e -> startTranscription(f));
                    recentMenu.add(item);
                }
            }
        }
    }

    private void handleExit() {
        if (hasUnsavedChanges) {
            int result = JOptionPane.showConfirmDialog(this,
                "You have unsaved changes. Exit anyway?",
                "Confirm Exit",
                JOptionPane.YES_NO_OPTION);
            
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        
        dispose();
        System.exit(0);
    }

    private String getSuggestedFilename() {
        if (currentAudioFile != null) {
            String name = currentAudioFile.getName();
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) + "_transcript" : name + "_transcript";
        }
        return "transcript";
    }

    private String formatTimestamp(long ms) {
        long hours = ms / 3600000;
        long minutes = (ms % 3600000) / 60000;
        long seconds = (ms % 60000) / 1000;
        
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void styleButton(JButton button, Color color) {
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.setMargin(new java.awt.Insets(6, 12, 6, 12));
        
        // Add hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            private Color originalColor = color;
            
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(darken(originalColor, 0.2));
            }
            
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(originalColor);
            }
        });
    }

    private Color darken(Color color, double factor) {
        return new Color(
            Math.max(0, (int) (color.getRed() * (1 - factor))),
            Math.max(0, (int) (color.getGreen() * (1 - factor))),
            Math.max(0, (int) (color.getBlue() * (1 - factor)))
        );
    }
}
