package atri.palaash.jvosk.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptExporterTest {

    private static final String TRANSCRIPT =
            "Hello, world!\n\nCafé — 日本語 transcript with \"quotes\" and a backslash \\.\n";

    private static final List<TranscriptExporter.TranscriptSegment> SEGMENTS = List.of(
            new TranscriptExporter.TranscriptSegment("Hello, world!", 0, 1234),
            new TranscriptExporter.TranscriptSegment(
                    "He said \"hi\" and left a backslash \\ here.", 3_723_456, 3_729_456),
            new TranscriptExporter.TranscriptSegment("Café — 日本語", 3_600_000, 3_601_500)
    );

    @TempDir
    Path tempDir;

    @Test
    void textGolden() throws IOException {
        assertEquals(golden("transcript.txt"), TranscriptExporter.toText(TRANSCRIPT));
    }

    @Test
    void srtGolden() throws IOException {
        assertEquals(golden("transcript.srt"), TranscriptExporter.toSRT(SEGMENTS));
    }

    @Test
    void vttGolden() throws IOException {
        assertEquals(golden("transcript.vtt"), TranscriptExporter.toVTT(SEGMENTS));
    }

    @Test
    void jsonGolden() throws IOException {
        String actual = TranscriptExporter.toJSON(SEGMENTS);
        assertEquals(golden("transcript.json"), actual);

        JsonNode root = new ObjectMapper().readTree(actual);
        assertEquals(3, root.get("segments").size());
        assertEquals("He said \"hi\" and left a backslash \\ here.",
                root.get("segments").get(1).get("text").asText());
        assertEquals(3_729_456, root.get("segments").get(1).get("end").asLong());
    }

    @Test
    void markdownGolden() throws IOException {
        assertEquals(golden("transcript.md"), TranscriptExporter.toMarkdown(TRANSCRIPT));
    }

    @Test
    void fileWritersMatchFormattersAndUseUtf8WithoutBom() throws IOException {
        assertFileExport("transcript.txt", file -> TranscriptExporter.exportAsText(TRANSCRIPT, file));
        assertFileExport("transcript.srt", file -> TranscriptExporter.exportAsSRT(SEGMENTS, file));
        assertFileExport("transcript.vtt", file -> TranscriptExporter.exportAsVTT(SEGMENTS, file));
        assertFileExport("transcript.json", file -> TranscriptExporter.exportAsJSON(SEGMENTS, file));
        assertFileExport("transcript.md", file -> TranscriptExporter.exportAsMarkdown(TRANSCRIPT, file));
    }

    @Test
    void jsonEscapesQuotesBackslashesAndControlCharacters() throws IOException {
        String text = "quote \" slash \\ newline\n tab\t control\u0001";
        String json = TranscriptExporter.toJSON(
                List.of(new TranscriptExporter.TranscriptSegment(text, 0, 10)));

        JsonNode root = new ObjectMapper().readTree(json);
        assertEquals(text, root.get("segments").get(0).get("text").asText());
        assertTrue(json.contains("\\\""));
        assertTrue(json.contains("\\\\"));
        assertTrue(json.contains("\\u0001"));
    }

    @Test
    void emptyExportsAreWellFormed() {
        assertEquals("", TranscriptExporter.toSRT(List.of()));
        assertEquals("WEBVTT\n\n", TranscriptExporter.toVTT(List.of()));
        assertEquals("{\n  \"segments\": [\n  ]\n}", TranscriptExporter.toJSON(List.of()));
    }

    @Test
    void negativeTimesAreClamped() {
        TranscriptExporter.TranscriptSegment segment =
                new TranscriptExporter.TranscriptSegment("negative", -100, -1);

        assertEquals(0, segment.startTimeMs);
        assertEquals(0, segment.endTimeMs);
        assertEquals("00:00:00,000", TranscriptExporter.formatSRTTime(-42));
        assertEquals("00:00:00.000", TranscriptExporter.formatVTTTime(-42));
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> TranscriptExporter.toText(null));
        assertThrows(NullPointerException.class, () -> TranscriptExporter.toSRT(null));
        assertThrows(NullPointerException.class,
                () -> new TranscriptExporter.TranscriptSegment(null, 0, 1));
    }

    @FunctionalInterface
    private interface FileExport {
        void write(File file) throws IOException;
    }

    private void assertFileExport(String goldenName, FileExport export) throws IOException {
        File output = tempDir.resolve("actual-" + goldenName).toFile();
        export.write(output);

        assertTrue(output.isFile());
        byte[] bytes = Files.readAllBytes(output.toPath());
        assertTrue(bytes.length > 0);
        assertFalse(bytes.length >= 3
                        && (bytes[0] & 0xFF) == 0xEF
                        && (bytes[1] & 0xFF) == 0xBB
                        && (bytes[2] & 0xFF) == 0xBF,
                "Export must not contain a UTF-8 BOM");

        assertArrayEquals(goldenBytes(goldenName), bytes);
    }

    private static String golden(String name) throws IOException {
        return new String(goldenBytes(name), StandardCharsets.UTF_8);
    }

    private static byte[] goldenBytes(String name) throws IOException {
        try (InputStream in = TranscriptExporterTest.class.getResourceAsStream("/golden/" + name)) {
            assertNotNull(in, "Missing golden resource: " + name);
            return in.readAllBytes();
        }
    }
}
