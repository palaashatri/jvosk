package dev.palaash.jvosk.ui;

import dev.palaash.jvosk.stt.VoskTranscriber;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.List;

public class MainFrame extends JFrame {

    private final JTextArea transcriptArea;
    private final JLabel statusLabel;
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
        dropPanel.setBorder(BorderFactory.createTitledBorder("Drop audio file here"));
        dropPanel.setPreferredSize(new Dimension(200, 120));
        dropPanel.setLayout(new GridBagLayout());
        dropPanel.add(new JLabel("Drag & drop an audio file (WAV/MP3) here"));

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
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(statusLabel, BorderLayout.WEST);
        content.add(statusPanel, BorderLayout.SOUTH);
    }

    private void startTranscription(File audioFile) {
        transcriptArea.setText("");
        setStatus("Transcribing: " + audioFile.getName());

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                transcriber.transcribeFile(audioFile, partial -> publish(partial));
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    transcriptArea.append(chunk + "\n");
                }
            }

            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions
                    setStatus("Done");
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String errorMsg = cause.getMessage();
                    setStatus("Error: " + errorMsg);
                    transcriptArea.setText("Transcription failed:\n" + errorMsg);
                    e.printStackTrace();
                }
            }
        };

        worker.execute();
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }
}
