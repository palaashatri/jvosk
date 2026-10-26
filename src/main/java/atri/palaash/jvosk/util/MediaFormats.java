package atri.palaash.jvosk.util;

import java.util.Locale;
import java.util.Set;

/**
 * File types the transcriber can open. Video is converted with the bundled
 * JAVE FFmpeg (no host ffmpeg). WAV is already PCM and skips conversion
 * unless speech enhancement is on.
 */
public final class MediaFormats {
    public static final String[] AUDIO_EXTENSIONS = {
            "wav", "mp3", "m4a", "flac", "ogg", "aac", "wma", "opus"
    };
    public static final String[] VIDEO_EXTENSIONS = {
            "mp4", "mkv", "mov", "webm", "avi", "m4v"
    };

    private static final Set<String> AUDIO = Set.of(AUDIO_EXTENSIONS);
    private static final Set<String> VIDEO = Set.of(VIDEO_EXTENSIONS);

    private MediaFormats() {}

    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isAudio(String fileName) {
        return AUDIO.contains(extensionOf(fileName));
    }

    public static boolean isVideo(String fileName) {
        return VIDEO.contains(extensionOf(fileName));
    }

    public static boolean isSupported(String fileName) {
        return isAudio(fileName) || isVideo(fileName);
    }

    public static boolean needsConversion(String fileName) {
        String ext = extensionOf(fileName);
        return !ext.equals("wav") && (AUDIO.contains(ext) || VIDEO.contains(ext));
    }

    public static String[] allExtensions() {
        String[] all = new String[AUDIO_EXTENSIONS.length + VIDEO_EXTENSIONS.length];
        System.arraycopy(AUDIO_EXTENSIONS, 0, all, 0, AUDIO_EXTENSIONS.length);
        System.arraycopy(VIDEO_EXTENSIONS, 0, all, AUDIO_EXTENSIONS.length, VIDEO_EXTENSIONS.length);
        return all;
    }
}
