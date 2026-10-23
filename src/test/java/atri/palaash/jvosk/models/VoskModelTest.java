package atri.palaash.jvosk.models;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoskModelTest {

    private static VoskModel model(String name, String size, VoskModel.ModelType type) {
        return new VoskModel.Builder()
                .name(name)
                .language("English")
                .size(size)
                .downloadUrl("https://example.test/" + name + ".zip")
                .type(type)
                .build();
    }

    @Test
    void parsesDisplaySizes() {
        assertEquals(40L * 1024 * 1024, VoskModel.parseSizeToBytes("40M"));
        assertEquals(Math.round(1.8 * 1024 * 1024 * 1024), VoskModel.parseSizeToBytes("1.8G"));
        assertEquals(Math.round(1.3 * 1024 * 1024 * 1024), VoskModel.parseSizeToBytes("1.3GiB"));
        assertEquals(512L * 1024, VoskModel.parseSizeToBytes("512K"));
        assertEquals(2L * 1024 * 1024 * 1024 * 1024, VoskModel.parseSizeToBytes("2TB"));
        assertEquals(1234, VoskModel.parseSizeToBytes("1234"));
    }

    @Test
    void rejectsOrIgnoresUnparseableSizes() {
        assertEquals(0, VoskModel.parseSizeToBytes(null));
        assertEquals(0, VoskModel.parseSizeToBytes(""));
        assertEquals(0, VoskModel.parseSizeToBytes("   "));
        assertEquals(0, VoskModel.parseSizeToBytes("Unknown"));
        assertEquals(0, VoskModel.parseSizeToBytes("1,8G"));
        assertEquals(0, VoskModel.parseSizeToBytes("-1G"));
    }

    @Test
    void exactSizeOverridesRoundedDisplaySize() {
        VoskModel model = new VoskModel.Builder()
                .name("vosk-model-small-en-us-0.15")
                .size("40M")
                .sizeBytes(41_205_931)
                .downloadUrl("https://example.test/model.zip")
                .build();

        assertEquals(41_205_931, model.getSizeBytes());
    }

    @Test
    void sizeDoesNotOverwriteExactSizeWhenSetFirst() {
        VoskModel model = new VoskModel.Builder()
                .name("vosk-model-small-en-us-0.15")
                .sizeBytes(41_205_931)
                .size("40M")
                .downloadUrl("https://example.test/model.zip")
                .build();

        assertEquals(41_205_931, model.getSizeBytes());
    }

    @Test
    void negativeExactSizeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new VoskModel.Builder().sizeBytes(-1));
    }

    @Test
    void checksumIsNormalisedAndOptional() {
        VoskModel withChecksum = new VoskModel.Builder()
                .name("vosk-model-small-en-us-0.15")
                .downloadUrl("https://example.test/model.zip")
                .checksumMd5("09AB50CCD62B674CBA A231B825F9C1CB".replace(" ", ""))
                .build();
        assertEquals("09ab50ccd62b674cbaa231b825f9c1cb", withChecksum.getChecksumMd5());

        VoskModel withoutChecksum = new VoskModel.Builder()
                .name("vosk-model-small-en-us-0.15")
                .downloadUrl("https://example.test/model.zip")
                .checksumMd5("  ")
                .build();
        assertNull(withoutChecksum.getChecksumMd5());
    }

    @Test
    void installedCopyPreservesCatalogueMetadata() {
        VoskModel original = new VoskModel.Builder()
                .name("vosk-model-small-en-us-0.15")
                .language("US English")
                .size("39.3MiB")
                .sizeBytes(41_205_931)
                .description("Version 0.15")
                .downloadUrl("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip")
                .checksumMd5("09ab50ccd62b674cbaa231b825f9c1cb")
                .type(VoskModel.ModelType.SMALL)
                .build();

        VoskModel installed = original.withInstalled("0.15");

        assertTrue(installed.isInstalled());
        assertEquals("0.15", installed.getInstalledVersion());
        assertEquals(original.getName(), installed.getName());
        assertEquals(original.getLanguage(), installed.getLanguage());
        assertEquals(original.getSizeBytes(), installed.getSizeBytes());
        assertEquals(original.getChecksumMd5(), installed.getChecksumMd5());
        assertEquals(original.getDownloadUrl(), installed.getDownloadUrl());
        assertFalse(original.isInstalled());
    }

    @Test
    void rejectsUnsafeModelNames() {
        assertFalse(VoskModel.isSafeModelName("../evil"));
        assertFalse(VoskModel.isSafeModelName("dir/model"));
        assertFalse(VoskModel.isSafeModelName("dir\\model"));
        assertFalse(VoskModel.isSafeModelName(".."));
        assertFalse(VoskModel.isSafeModelName(".hidden"));
        assertFalse(VoskModel.isSafeModelName(""));
        assertFalse(VoskModel.isSafeModelName(null));

        assertTrue(VoskModel.isSafeModelName("vosk-model-small-en-us-0.15"));
        assertTrue(VoskModel.isSafeModelName("model_1.2-test"));

        assertThrows(IllegalArgumentException.class, () -> new VoskModel.Builder()
                .name("../evil")
                .downloadUrl("https://example.test/model.zip")
                .build());
        assertThrows(NullPointerException.class, () -> new VoskModel.Builder()
                .downloadUrl("https://example.test/model.zip")
                .build());
        assertThrows(NullPointerException.class, () -> new VoskModel.Builder()
                .name("valid")
                .build());
    }

    @Test
    void modelsSortByLanguageThenTypeThenName() {
        VoskModel englishBig = model("vosk-model-en-us-0.22", "1.8G", VoskModel.ModelType.BIG);
        VoskModel englishSmallB = model("vosk-model-small-en-us-b", "40M", VoskModel.ModelType.SMALL);
        VoskModel englishSmallA = model("vosk-model-small-en-us-a", "40M", VoskModel.ModelType.SMALL);
        VoskModel german = new VoskModel.Builder()
                .name("vosk-model-small-de-0.15")
                .language("German")
                .size("45M")
                .downloadUrl("https://example.test/de.zip")
                .type(VoskModel.ModelType.SMALL)
                .build();

        List<VoskModel> sorted = List.of(englishBig, englishSmallB, german, englishSmallA)
                .stream()
                .sorted()
                .toList();

        assertEquals(List.of(englishSmallA, englishSmallB, englishBig, german), sorted);
    }

    @Test
    void equalityIsBasedOnNameOnly() {
        VoskModel first = model("vosk-model-small-en-us-0.15", "40M", VoskModel.ModelType.SMALL);
        VoskModel second = new VoskModel.Builder()
                .name("vosk-model-small-en-us-0.15")
                .language("Different")
                .size("99G")
                .downloadUrl("https://example.test/other.zip")
                .type(VoskModel.ModelType.BIG)
                .build();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, model("vosk-model-small-de-0.15", "45M", VoskModel.ModelType.SMALL));
    }

    @Test
    void bigModelDetectionUsesTypeAndExactSize() {
        assertTrue(model("vosk-model-en-us-0.22", "1.8G", VoskModel.ModelType.BIG).isBigModel());
        assertTrue(new VoskModel.Builder()
                .name("large")
                .size("0M")
                .sizeBytes(500_000_001)
                .downloadUrl("https://example.test/large.zip")
                .type(VoskModel.ModelType.SMALL)
                .build()
                .isBigModel());
        assertFalse(model("vosk-model-small-en-us-0.15", "40M", VoskModel.ModelType.SMALL).isBigModel());
    }
}
