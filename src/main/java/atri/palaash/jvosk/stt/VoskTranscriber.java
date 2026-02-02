package atri.palaash.jvosk.stt;

import org.vosk.Model;
import org.vosk.Recognizer;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.util.function.Consumer;

public class VoskTranscriber {

    private Model model;
    private String currentModelPath;

    public VoskTranscriber(String modelPath) {
        this.currentModelPath = modelPath;
        loadModel(modelPath);
    }
    
    public VoskTranscriber(Model model) {
        this.model = model;
        this.currentModelPath = null;
    }
    
    private void loadModel(String modelPath) {
        try {
            this.model = new Model(modelPath);
            this.currentModelPath = modelPath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Vosk model: " + modelPath, e);
        }
    }
    
    /**
     * Switch to a different model.
     */
    public void switchModel(Model newModel) {
        if (this.model != null && this.currentModelPath != null) {
            this.model.close();
        }
        this.model = newModel;
        this.currentModelPath = null;
    }
    
    /**
     * Switch to a different model by path.
     */
    public void switchModel(String modelPath) {
        if (this.model != null && this.currentModelPath != null) {
            this.model.close();
        }
        loadModel(modelPath);
    }
    
    public String getCurrentModelPath() {
        return currentModelPath;
    }
    
    public Model getModel() {
        return model;
    }


    public void transcribeFile(File audioFile, Consumer<String> onText) throws InterruptedException {
        File tempWav = null;
        
        try {
            String fileName = audioFile.getName().toLowerCase();
            File fileToTranscribe = audioFile;
            
            // Convert non-WAV files using JAVE2
            if (needsConversion(fileName)) {
                tempWav = convertToWavWithJave(audioFile);
                fileToTranscribe = tempWav;
            }
            
            // Transcribe WAV file
            transcribeWav(fileToTranscribe, onText);
            
        } catch (InterruptedException e) {
            // Re-throw interruption to allow proper cancellation handling
            throw e;
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
    
    private boolean needsConversion(String fileName) {
        return fileName.endsWith(".mp3") || 
               fileName.endsWith(".m4a") || 
               fileName.endsWith(".flac") || 
               fileName.endsWith(".ogg") || 
               fileName.endsWith(".aac") || 
               fileName.endsWith(".wma") ||
               fileName.endsWith(".opus");
    }
    
    private File convertToWavWithJave(File audioFile) throws IOException, InterruptedException {
        try {
            // Check for cancellation before starting conversion
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Conversion cancelled by user");
            }
            
            // Create temp WAV file
            File tempWav = File.createTempFile("jvosk_", ".wav");
            
            System.out.println("Converting " + audioFile.getName() + " to WAV format...");
            
            // Configure audio attributes: 16kHz, mono, 16-bit PCM
            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("pcm_s16le");      // 16-bit signed PCM little-endian
            audio.setSamplingRate(16000);     // 16kHz sample rate
            audio.setChannels(1);             // Mono
            audio.setBitRate(256000);         // Standard bit rate
            
            // Configure encoding attributes
            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setOutputFormat("wav");
            attrs.setAudioAttributes(audio);
            
            // Perform conversion
            Encoder encoder = new Encoder();
            encoder.encode(new MultimediaObject(audioFile), tempWav, attrs);
            
            // Check for cancellation after conversion
            if (Thread.currentThread().isInterrupted()) {
                // Clean up temp file if cancelled
                if (tempWav.exists()) {
                    tempWav.delete();
                }
                throw new InterruptedException("Conversion cancelled by user");
            }
            
            System.out.println("Conversion complete. Starting transcription...");
            return tempWav;
            
        } catch (EncoderException e) {
            throw new IOException(
                "Audio conversion failed. The file may be corrupted or in an unsupported format.\n" +
                "Error: " + e.getMessage(), e
            );
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

            try (Recognizer recognizer = new Recognizer(model, 16000)) {
                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = convertedStream.read(buffer)) >= 0) {
                // Check for thread interruption (cancellation)
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Transcription cancelled by user");
                }
                
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
