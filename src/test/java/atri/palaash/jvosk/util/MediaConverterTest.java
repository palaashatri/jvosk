package atri.palaash.jvosk.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaConverterTest {

    @TempDir
    Path tempDir;

    @Test
    void convertsWavThroughBundledFfmpegWithSpeechFilter() throws Exception {
        File source = writeSilenceWav(tempDir.resolve("source.wav").toFile(), 16000, 0.2);
        File converted = MediaConverter.toPcmWav(source, true);
        try {
            assertTrue(converted.isFile());
            assertTrue(converted.length() > 44);
            AudioFileFormat format = AudioSystem.getAudioFileFormat(converted);
            assertEquals(16000f, format.getFormat().getSampleRate(), 0.1);
            assertEquals(1, format.getFormat().getChannels());
            assertEquals(16, format.getFormat().getSampleSizeInBits());
        } finally {
            converted.delete();
        }
    }

    private static File writeSilenceWav(File file, int sampleRate, double seconds) throws IOException {
        int frames = (int) (sampleRate * seconds);
        byte[] pcm = new byte[frames * 2];
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(pcm), format, frames)) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, file);
        }
        return file;
    }
}
