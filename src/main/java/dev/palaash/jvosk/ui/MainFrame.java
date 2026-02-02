package atri.palaash.jvosk.ui;

import atri.palaash.jvosk.stt.VoskTranscriber;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.List;

public class MainFrame extends JFrame {

    private final JTextArea transcriptArea;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final VoskTranscriber transcriber;

    public MainFrame() {
        super("jvosk – Speech‑to‑Text");

        this.transcriber = new VoskTranscriber("models/vosk-model-small-en-us-0.15");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(content);

        JPanel dropPanel = new JPanel();
        dropPanel.setBorder(BorderFactory.createTitledBorder("Select or drop audio file"));
        dropPanel.setPreferredSize(new Dimension(200, 120));
        dropPanel.setLayout(new BorderLayout(8, 8));
        
        JLabel dropLabel = new JLabel("Drag & drop an audio file here", SwingConstants.CENTER);
        dropPanel.add(dropLabel, BorderLayout.CENTER);
        
        JButton browseButton = new JButton("Browse Files...");
        browseButton.addActionListener(e -> browseForFile());
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(browseButton);
        dropPanel.add(buttonPanel, BorderLayout.SOUTH);

        dropPanel.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
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

        content.add(dropPanel, BorderLayout.NORTH);

        transcriptArea = new JTextArea();
        transcriptArea.setEditable(false);
        transcriptArea.setLineWrap(true);
        transcriptArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(transcriptArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Transcript"));
        content.add(scrollPane, BorderLayout.CENTER);

        statusLabel = new JLabel("Ready");
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        
        JPanel statusPanel = new JPanel(new BorderLayout(8, 0));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);
        content.add(statusPanel, BorderLayout.SOUTH);
    }

    private void startTranscription(File audioFile) {
        transcriptArea.setText("");
        setStatus("Processing: " + audioFile.getName());
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Converting audio...");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                transcriber.transcribeFile(audioFile, partial -> {
                    // Switch to transcription mode on first chunk
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setIndeterminate(false);
                        progressBar.setString("Transcribing...");
                    });
                    publish(partial);
                });
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    transcriptArea.append(chunk + "\n");
                }
                // Update progress based on text length as rough estimate
                int progress = Math.min(90, transcriptArea.getText().length() / 10);
                progressBar.setValue(progress);
            }

            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions
                    progressBar.setValue(100);
                    progressBar.setString("Complete");
                    setStatus("Done");
                    // Hide progress bar after a delay
                    Timer timer = new Timer(2000, e -> progressBar.setVisible(false));
                    timer.setRepeats(false);
                    timer.start();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String errorMsg = cause.getMessage();
                    progressBar.setVisible(false);
                    setStatus("Error: " + errorMsg);
                    transcriptArea.setText("Transcription failed:\n" + errorMsg);
                    e.printStackTrace();
                }
            }
        };

        worker.execute();
    }

    private void browseForFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Audio File");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                return name.endsWith(".wav") || 
                       name.endsWith(".mp3") || 
                       name.endsWith(".m4a") || 
                       name.endsWith(".flac") || 
                       name.endsWith(".ogg") || 
                       name.endsWith(".aac") || 
                       name.endsWith(".wma") ||
                       name.endsWith(".opus");
            }
            
            @Override
            public String getDescription() {
                return "Audio Files (*.wav, *.mp3, *.m4a, *.flac, *.ogg, *.aac, *.wma, *.opus)";
            }
        });
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            startTranscription(fileChooser.getSelectedFile());
        }
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }
}
