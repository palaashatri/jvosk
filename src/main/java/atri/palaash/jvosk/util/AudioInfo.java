package atri.palaash.jvosk.util;

import ws.schild.jave.MultimediaObject;
import ws.schild.jave.info.MultimediaInfo;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import java.io.File;

public class AudioInfo {
    
    private final long durationSeconds;
    private final String format;
    private final int sampleRate;
    private final int channels;
    private final long fileSizeBytes;
    
    private AudioInfo(long durationSeconds, String format, int sampleRate, int channels, long fileSizeBytes) {
        this.durationSeconds = durationSeconds;
        this.format = format;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.fileSizeBytes = fileSizeBytes;
    }
    
    public static AudioInfo from(File audioFile) {
        try {
            // Try JAVE2 first for better format support
            MultimediaObject obj = new MultimediaObject(audioFile);
            MultimediaInfo info = obj.getInfo();
            
            long duration = info.getDuration() / 1000; // Convert ms to seconds
            String format = audioFile.getName().substring(audioFile.getName().lastIndexOf('.') + 1).toUpperCase();
            int sampleRate = info.getAudio() != null ? info.getAudio().getSamplingRate() : 0;
            int channels = info.getAudio() != null ? info.getAudio().getChannels() : 0;
            
            return new AudioInfo(duration, format, sampleRate, channels, audioFile.length());
            
        } catch (Exception e) {
            // Fallback to Java Sound API for WAV files
            try {
                AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(audioFile);
                long frames = fileFormat.getFrameLength();
                float frameRate = fileFormat.getFormat().getFrameRate();
                long duration = frames > 0 && frameRate > 0 ? (long) (frames / frameRate) : 0;
                
                String format = fileFormat.getType().toString();
                int sampleRate = (int) fileFormat.getFormat().getSampleRate();
                int channels = fileFormat.getFormat().getChannels();
                
                return new AudioInfo(duration, format, sampleRate, channels, audioFile.length());
            } catch (Exception ex) {
                return new AudioInfo(0, "UNKNOWN", 0, 0, audioFile.length());
            }
        }
    }
    
    public long getDurationSeconds() {
        return durationSeconds;
    }
    
    public String getFormat() {
        return format;
    }
    
    public int getSampleRate() {
        return sampleRate;
    }
    
    public int getChannels() {
        return channels;
    }
    
    public long getFileSizeBytes() {
        return fileSizeBytes;
    }
    
    public String getFormattedDuration() {
        long hours = durationSeconds / 3600;
        long minutes = (durationSeconds % 3600) / 60;
        long seconds = durationSeconds % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
    
    public String getFormattedSize() {
        if (fileSizeBytes < 1024) {
            return fileSizeBytes + " B";
        } else if (fileSizeBytes < 1024 * 1024) {
            return String.format("%.1f KB", fileSizeBytes / 1024.0);
        } else {
            return String.format("%.1f MB", fileSizeBytes / (1024.0 * 1024.0));
        }
    }
    
    public long getEstimatedTranscriptionSeconds() {
        // Rough estimate: transcription takes ~0.3x real-time for Vosk
        return (long) (durationSeconds * 0.3);
    }
    
    @Override
    public String toString() {
        return String.format("%s | %s | %d Hz | %d ch | %s", 
            format, getFormattedDuration(), sampleRate, channels, getFormattedSize());
    }
}
