package dev.palaash.jvosk.stt;

import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.util.function.Consumer;

public class VoskTranscriber {

    private final Model model;

  public VoskTranscriber(String modelPath) {
    try {
        this.model = new Model(modelPath);
    } catch (IOException e) {
        throw new RuntimeException("Failed to load Vosk model: " + modelPath, e);
    }
}


    public void transcribeFile(File audioFile, Consumer<String> onText) {
        File tempWav = null;
        
        try {
            String fileName = audioFile.getName().toLowerCase();
            File fileToTranscribe = audioFile;
            
            // For MP3/M4A files, convert using ffmpeg
            if (fileName.endsWith(".mp3") || fileName.endsWith(".m4a")) {
                tempWav = convertToWavWithFfmpeg(audioFile);
                fileToTranscribe = tempWav;
            }
            
            // Transcribe WAV file
            transcribeWav(fileToTranscribe, onText);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to read audio file: " + e.getMessage(), e);
        } finally {
            // Clean up temporary file
            if (tempWav != null && tempWav.exists()) {
                try {
                    Files.delete(tempWav.toPath());
                } catch (IOException ignored) {}
            }
        }
    }
    
    private File convertToWavWithFfmpeg(File audioFile) throws IOException, InterruptedException {
        // Check if ffmpeg is available
        if (!isFfmpegAvailable()) {
            throw new IOException(
                "FFmpeg is required to process MP3/M4A files but is not installed.\\n\\n" +
                "Installation instructions:\\n" +
                "  macOS:   brew install ffmpeg\\n" +
                "  Linux:   sudo apt install ffmpeg\\n" +
                "  Windows: Download from https://ffmpeg.org/download.html\\n\\n" +
                "Alternatively, convert your audio to WAV format first."
            );
        }
        
        // Create temp WAV file
        File tempWav = File.createTempFile("jvosk_", ".wav");
        
        System.out.println("Converting " + audioFile.getName() + " to WAV format...");
        
        // Convert: any audio -> 16kHz mono 16-bit PCM WAV
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg",
            "-i", audioFile.getAbsolutePath(),
            "-ar", "16000",           // 16kHz sample rate
            "-ac", "1",                // Mono
            "-sample_fmt", "s16",      // 16-bit signed PCM
            "-f", "wav",               // WAV format
            "-y",                      // Overwrite output
            tempWav.getAbsolutePath()
        );
        
        // Redirect stderr to avoid clutter (ffmpeg outputs to stderr)
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        Process process = pb.start();
        
        // Consume error stream to prevent blocking
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Optionally log ffmpeg output
                    if (line.contains("error") || line.contains("Error")) {
                        System.err.println("FFmpeg: " + line);
                    }
                }
            } catch (IOException ignored) {}
        }).start();
        
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            tempWav.delete();
            throw new IOException("Audio conversion failed. The file may be corrupted or in an unsupported format.");
        }
        
        System.out.println("Conversion complete. Starting transcription...");
        return tempWav;
    }
    
    private boolean isFfmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void transcribeWav(File wavFile, Consumer<String> onText) throws Exception {
        AudioInputStream ais = null;
        AudioInputStream convertedStream = null;
        
        try {
            ais = AudioSystem.getAudioInputStream(wavFile);
            AudioFormat sourceFormat = ais.getFormat();
            
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    16000,
                    16,
                    1,
                    2,
                    16000,
                    false
            );

            // Convert if needed
            if (!sourceFormat.matches(targetFormat)) {
                convertedStream = AudioSystem.getAudioInputStream(targetFormat, ais);
            } else {
                convertedStream = ais;
            }

            Recognizer recognizer = new Recognizer(model, 16000);
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = convertedStream.read(buffer)) >= 0) {
                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    String result = extractText(recognizer.getResult());
                    if (!result.isEmpty()) {
                        onText.accept(result);
                    }
                }
            }

            String finalResult = extractText(recognizer.getFinalResult());
            if (!finalResult.isEmpty()) {
                onText.accept(finalResult);
            }

        } finally {
            if (convertedStream != null && convertedStream != ais) {
                try { convertedStream.close(); } catch (IOException ignored) {}
            }
            if (ais != null) {
                try { ais.close(); } catch (IOException ignored) {}
            }
        }
    }

    private String extractText(String json) {
        int idx = json.indexOf("\"text\"");
        if (idx == -1) return "";
        int start = json.indexOf('"', idx + 6);
        int end = json.indexOf('"', start + 1);
        if (start == -1 || end == -1) return "";
        return json.substring(start + 1, end);
    }
}
