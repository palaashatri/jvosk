package dev.palaash.jvosk.stt;

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
            
            // Convert non-WAV files using JAVE2
            if (needsConversion(fileName)) {
                tempWav = convertToWavWithJave(audioFile);
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
    
    private boolean needsConversion(String fileName) {
        return fileName.endsWith(".mp3") || 
               fileName.endsWith(".m4a") || 
               fileName.endsWith(".flac") || 
               fileName.endsWith(".ogg") || 
               fileName.endsWith(".aac") || 
               fileName.endsWith(".wma") ||
               fileName.endsWith(".opus");
    }
    
    private File convertToWavWithJave(File audioFile) throws IOException {
        try {
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
