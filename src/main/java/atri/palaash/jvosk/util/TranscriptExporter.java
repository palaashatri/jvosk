package atri.palaash.jvosk.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

/**
 * Pure transcript formatters plus small file-writing helpers.
 *
 * <p>Every {@code toXxx} method is deterministic and platform-independent:
 * it always emits {@code \n} line endings and UTF-8 text. The file-based
 * methods simply write those strings, which keeps exports easy to test.</p>
 */
public class TranscriptExporter {

    public static class TranscriptSegment {
        public final String text;
        public final long startTimeMs;
        public final long endTimeMs;

        public TranscriptSegment(String text, long startTimeMs, long endTimeMs) {
            this.text = Objects.requireNonNull(text, "Segment text is required");
            this.startTimeMs = Math.max(0, startTimeMs);
            this.endTimeMs = Math.max(0, endTimeMs);
        }
    }

    public static void exportAsText(String content, File outputFile) throws IOException {
        writeString(outputFile, toText(content));
    }

    public static void exportAsSRT(List<TranscriptSegment> segments, File outputFile) throws IOException {
        writeString(outputFile, toSRT(segments));
    }

    public static void exportAsVTT(List<TranscriptSegment> segments, File outputFile) throws IOException {
        writeString(outputFile, toVTT(segments));
    }

    public static void exportAsJSON(List<TranscriptSegment> segments, File outputFile) throws IOException {
        writeString(outputFile, toJSON(segments));
    }

    public static void exportAsMarkdown(String content, File outputFile) throws IOException {
        writeString(outputFile, toMarkdown(content));
    }

    public static String toText(String content) {
        return Objects.requireNonNull(content, "Transcript content is required");
    }

    public static String toSRT(List<TranscriptSegment> segments) {
        Objects.requireNonNull(segments, "segments");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            TranscriptSegment seg = segments.get(i);
            out.append(i + 1).append('\n');
            out.append(formatSRTTime(seg.startTimeMs))
                    .append(" --> ")
                    .append(formatSRTTime(seg.endTimeMs))
                    .append('\n');
            out.append(seg.text).append("\n\n");
        }
        return out.toString();
    }

    public static String toVTT(List<TranscriptSegment> segments) {
        Objects.requireNonNull(segments, "segments");
        StringBuilder out = new StringBuilder("WEBVTT\n\n");
        for (TranscriptSegment seg : segments) {
            out.append(formatVTTTime(seg.startTimeMs))
                    .append(" --> ")
                    .append(formatVTTTime(seg.endTimeMs))
                    .append('\n');
            out.append(seg.text).append("\n\n");
        }
        return out.toString();
    }

    public static String toJSON(List<TranscriptSegment> segments) {
        Objects.requireNonNull(segments, "segments");
        StringBuilder out = new StringBuilder("{\n  \"segments\": [\n");
        for (int i = 0; i < segments.size(); i++) {
            TranscriptSegment seg = segments.get(i);
            out.append("    {\"text\": \"")
                    .append(escapeJson(seg.text))
                    .append("\", \"start\": ")
                    .append(seg.startTimeMs)
                    .append(", \"end\": ")
                    .append(seg.endTimeMs)
                    .append('}')
                    .append(i < segments.size() - 1 ? "," : "")
                    .append('\n');
        }
        out.append("  ]\n}");
        return out.toString();
    }

    public static String toMarkdown(String content) {
        Objects.requireNonNull(content, "Transcript content is required");
        return "# Transcription\n\n" + content;
    }

    private static void writeString(File outputFile, String content) throws IOException {
        Objects.requireNonNull(outputFile, "outputFile");
        Files.writeString(outputFile.toPath(), content, StandardCharsets.UTF_8);
    }

    static String formatSRTTime(long ms) {
        long safeMs = Math.max(0, ms);
        long hours = safeMs / 3600000;
        long minutes = (safeMs % 3600000) / 60000;
        long seconds = (safeMs % 60000) / 1000;
        long millis = safeMs % 1000;
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
    }

    static String formatVTTTime(long ms) {
        long safeMs = Math.max(0, ms);
        long hours = safeMs / 3600000;
        long minutes = (safeMs % 3600000) / 60000;
        long seconds = (safeMs % 60000) / 1000;
        long millis = safeMs % 1000;
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }

    private static String escapeJson(String text) {
        StringBuilder escaped = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
