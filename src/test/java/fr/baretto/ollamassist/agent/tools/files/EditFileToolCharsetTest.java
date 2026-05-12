package fr.baretto.ollamassist.agent.tools.files;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EditFileTool#detectCharset(byte[])}.
 *
 * Covers the encoding detection logic added in F-3 of the 2026-04-27 audit:
 * UTF-8, UTF-8 BOM, ISO-8859-1, and Windows-1252 files.
 */
class EditFileToolCharsetTest {

    // -------------------------------------------------------------------------
    // UTF-8 detection
    // -------------------------------------------------------------------------

    @Test
    void detectCharset_plainAscii_returnsUtf8() {
        byte[] bytes = "public class Foo {}".getBytes(StandardCharsets.UTF_8);
        assertThat(EditFileTool.detectCharset(bytes)).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void detectCharset_utf8MultibyteChars_returnsUtf8() {
        // "résumé" encoded as UTF-8 — é is 0xC3 0xA9 (valid 2-byte UTF-8 sequence)
        byte[] bytes = "résumé".getBytes(StandardCharsets.UTF_8);
        assertThat(EditFileTool.detectCharset(bytes)).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void detectCharset_utf8WithBom_returnsUtf8() {
        // BOM = EF BB BF followed by regular content; UTF-8 decoder accepts BOM as U+FEFF
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[3 + content.length];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(content, 0, withBom, 3, content.length);

        assertThat(EditFileTool.detectCharset(withBom)).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void detectCharset_emptyBytes_returnsUtf8() {
        assertThat(EditFileTool.detectCharset(new byte[0])).isEqualTo(StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // Windows-1252 detection (F-3: bytes 0x80–0x9F)
    // -------------------------------------------------------------------------

    @Test
    void detectCharset_euroSignByte0x80_returnsWindows1252() {
        // 0x80 = € in Windows-1252 (undefined / control char in ISO-8859-1)
        byte[] bytes = new byte[]{0x70, 0x72, 0x69, 0x63, 0x65, 0x3A, (byte) 0x80};
        // "price:" + 0x80
        Charset detected = EditFileTool.detectCharset(bytes);
        assertThat(detected.name()).isEqualToIgnoringCase("windows-1252");
    }

    @Test
    void detectCharset_leftDoubleQuote0x93_returnsWindows1252() {
        // 0x93 = " (LEFT DOUBLE QUOTATION MARK) in Windows-1252
        byte[] bytes = new byte[]{(byte) 0x93, 0x68, 0x65, 0x6C, 0x6C, 0x6F, (byte) 0x94};
        Charset detected = EditFileTool.detectCharset(bytes);
        assertThat(detected.name()).isEqualToIgnoringCase("windows-1252");
    }

    @Test
    void detectCharset_allWindows1252SpecialBytes_returnsWindows1252() {
        // Build a byte array with one byte from each position in 0x80–0x9F
        byte[] bytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            bytes[i] = (byte) (0x80 + i);
        }
        Charset detected = EditFileTool.detectCharset(bytes);
        assertThat(detected.name()).isEqualToIgnoringCase("windows-1252");
    }

    // -------------------------------------------------------------------------
    // ISO-8859-1 detection (no bytes in 0x80–0x9F)
    // -------------------------------------------------------------------------

    @Test
    void detectCharset_latin1OnlyAboveA0_returnsIso88591() {
        // 0xE9 = é in ISO-8859-1 (also the same in Windows-1252, but NOT in 0x80–0x9F range)
        byte[] bytes = new byte[]{0x72, (byte) 0xE9, 0x73, 0x75, 0x6D, (byte) 0xE9}; // "résumé"
        assertThat(EditFileTool.detectCharset(bytes)).isEqualTo(StandardCharsets.ISO_8859_1);
    }

    @Test
    void detectCharset_latin1HighBytes_returnsIso88591() {
        // 0xA0–0xFF range: shared between ISO-8859-1 and Windows-1252
        byte[] bytes = new byte[]{(byte) 0xA1, (byte) 0xBF, (byte) 0xFF};
        assertThat(EditFileTool.detectCharset(bytes)).isEqualTo(StandardCharsets.ISO_8859_1);
    }

    // -------------------------------------------------------------------------
    // Round-trip preservation (regression for Q-1)
    // -------------------------------------------------------------------------

    @Test
    void detectCharset_windows1252File_roundTripPreservesEuroSign() throws Exception {
        // Build a Windows-1252 byte array containing "price: €100"
        // 0x80 = € in Windows-1252
        byte[] original = {0x70, 0x72, 0x69, 0x63, 0x65, 0x3A, 0x20, (byte) 0x80, 0x31, 0x30, 0x30};

        Charset detected = EditFileTool.detectCharset(original);
        String decoded = new String(original, detected);
        byte[] reencoded = decoded.getBytes(detected);

        assertThat(reencoded).isEqualTo(original);
    }

    @Test
    void detectCharset_iso88591File_roundTripPreservesBytes() throws Exception {
        // 0xE9 = é in ISO-8859-1
        byte[] original = {0x72, (byte) 0xE9, 0x73, 0x75, 0x6D, (byte) 0xE9};

        Charset detected = EditFileTool.detectCharset(original);
        String decoded = new String(original, detected);
        byte[] reencoded = decoded.getBytes(detected);

        assertThat(reencoded).isEqualTo(original);
    }
}
