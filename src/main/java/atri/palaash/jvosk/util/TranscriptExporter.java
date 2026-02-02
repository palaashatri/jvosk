package atri.palaash.jvosk.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class TranscriptExporter {
    
    public static class TranscriptSegment {
        public final String text;
        public final long startTimeMs;
        public final long endTimeMs;
        
        public TranscriptSegment(String text, long startTimeMs, long endTimeMs) {
            this.text = text;
            this.startTimeMs = startTimeMs;
            this.endTimeMs = endTimeMs;
        }
    }
    
    public static void exportAsText(String content, File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(content);
        }
    }
    
    public static void exportAsSRT(List<TranscriptSegment> segments, File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            for (int i = 0; i < segments.size(); i++) {
                TranscriptSegment seg = segments.get(i);
                writer.write(String.format("%d\n", i + 1));
                writer.write(String.format("%s --> %s\n", 
                    formatSRTTime(seg.startTimeMs), 
                    formatSRTTime(seg.endTimeMs)));
                writer.write(seg.text + "\n\n");
            }
        }
    }
    
    public static void exportAsVTT(List<TranscriptSegment> segments, File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("WEBVTT\n\n");
            for (TranscriptSegment seg : segments) {
                writer.write(String.format("%s --> %s\n", 
                    formatVTTTime(seg.startTimeMs), 
                    formatVTTTime(seg.endTimeMs)));
                writer.write(seg.text + "\n\n");
            }
        }
    }
    
    public static void exportAsJSON(List<TranscriptSegment> segments, File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("{\n  \"segments\": [\n");
            for (int i = 0; i < segments.size(); i++) {
                TranscriptSegment seg = segments.get(i);
                writer.write(String.format("    {\"text\": \"%s\", \"start\": %d, \"end\": %d}%s\n",
                    seg.text.replace("\"", "\\\""),
                    seg.startTimeMs,
                    seg.endTimeMs,
                    i < segments.size() - 1 ? "," : ""));
            }
            writer.write("  ]\n}");
        }
    }
    
    public static void exportAsMarkdown(String content, File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("# Transcription\n\n");
            writer.write(content);
        }
    }
    
    private static String formatSRTTime(long ms) {
        long hours = ms / 3600000;
        long minutes = (ms % 3600000) / 60000;
        long seconds = (ms % 60000) / 1000;
        long millis = ms % 1000;
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
    }
    
    private static String formatVTTTime(long ms) {
        long hours = ms / 3600000;
        long minutes = (ms % 3600000) / 60000;
        long seconds = (ms % 60000) / 1000;
        long millis = ms % 1000;
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }
}
