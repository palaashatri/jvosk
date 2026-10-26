package atri.palaash.jvosk.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaFormatsTest {

    @Test
    void classifiesAudioVideoAndConversion() {
        assertTrue(MediaFormats.isAudio("talk.wav"));
        assertTrue(MediaFormats.isAudio("song.MP3"));
        assertTrue(MediaFormats.isVideo("clip.mp4"));
        assertTrue(MediaFormats.isVideo("clip.MKV"));
        assertTrue(MediaFormats.isSupported("clip.webm"));
        assertFalse(MediaFormats.isSupported("notes.pdf"));
        assertFalse(MediaFormats.needsConversion("speech.wav"));
        assertTrue(MediaFormats.needsConversion("speech.mp3"));
        assertTrue(MediaFormats.needsConversion("interview.mp4"));
        assertArrayEquals(
                new String[] {"wav", "mp3", "m4a", "flac", "ogg", "aac", "wma", "opus",
                        "mp4", "mkv", "mov", "webm", "avi", "m4v"},
                MediaFormats.allExtensions());
    }
}
