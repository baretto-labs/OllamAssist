package fr.baretto.ollamassist.chat.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class CodeAwareDocumentSplitterTest {

    private CodeAwareDocumentSplitter splitter;

    @BeforeEach
    void setUp() {
        splitter = new CodeAwareDocumentSplitter(null);
    }

    // --- fallbackSplit ---

    @Test
    void fallbackSplit_nullText_returnsEmpty() {
        List<TextSegment> result = splitter.fallbackSplit(null, new Metadata());
        assertTrue(result.isEmpty());
    }

    @Test
    void fallbackSplit_blankText_returnsEmpty() {
        List<TextSegment> result = splitter.fallbackSplit("   \n  ", new Metadata());
        assertTrue(result.isEmpty());
    }

    @Test
    void fallbackSplit_shortText_producesOneChunk() {
        String text = "line1\nline2\nline3";
        List<TextSegment> result = splitter.fallbackSplit(text, new Metadata());

        assertEquals(1, result.size());
        assertEquals(CodeAwareDocumentSplitter.CHUNK_TYPE_OTHER, result.get(0).metadata().getString(CodeAwareDocumentSplitter.META_CHUNK_TYPE));
    }

    @Test
    void fallbackSplit_longText_producesMultipleChunks() {
        // 70 lines → 2 chunks (lines 1-60, lines 51-70)
        String text = IntStream.rangeClosed(1, 70)
                .mapToObj(i -> "line " + i)
                .collect(Collectors.joining("\n"));

        List<TextSegment> result = splitter.fallbackSplit(text, new Metadata());

        assertTrue(result.size() >= 2, "70 lines should produce at least 2 chunks");
    }

    @Test
    void fallbackSplit_chunksOverlap() {
        // 65 lines with FALLBACK_CHUNK_LINES=60, FALLBACK_OVERLAP_LINES=10
        // Chunk 1: lines 1–60, Chunk 2: lines 51–65
        String text = IntStream.rangeClosed(1, 65)
                .mapToObj(i -> "line " + i)
                .collect(Collectors.joining("\n"));

        List<TextSegment> result = splitter.fallbackSplit(text, new Metadata());

        assertEquals(2, result.size());
        assertTrue(result.get(0).text().contains("line 50"), "First chunk should include line 50");
        assertTrue(result.get(1).text().contains("line 51"), "Second chunk should start around line 51");
    }

    @Test
    void fallbackSplit_setsLineStartMetadata() {
        String text = IntStream.rangeClosed(1, 70)
                .mapToObj(i -> "line " + i)
                .collect(Collectors.joining("\n"));

        List<TextSegment> result = splitter.fallbackSplit(text, new Metadata());

        assertEquals("1", result.get(0).metadata().getString("line_start"));
        assertEquals("51", result.get(1).metadata().getString("line_start"));
    }

    @Test
    void fallbackSplit_copiesDocMetadata() {
        Metadata docMetadata = new Metadata();
        docMetadata.put("file_name", "MyClass.java");
        docMetadata.put("absolute_directory_path", "/src/main");

        List<TextSegment> result = splitter.fallbackSplit("line1\nline2", docMetadata);

        assertEquals(1, result.size());
        assertEquals("MyClass.java", result.get(0).metadata().getString("file_name"));
        assertEquals("/src/main", result.get(0).metadata().getString("absolute_directory_path"));
    }

    @Test
    void fallbackSplit_doesNotMutateOriginalMetadata() {
        Metadata original = new Metadata();
        original.put("key", "value");

        splitter.fallbackSplit("line1", original);

        assertEquals("value", original.getString("key"));
        assertNull(original.getString(CodeAwareDocumentSplitter.META_CHUNK_TYPE),
                "Original metadata must not be mutated");
    }

    // --- truncation ---

    @Test
    void fallbackSplit_truncatesChunkAt3000Chars() {
        // Build a chunk that exceeds MAX_CHUNK_CHARS=3000 within 60 lines
        String longLine = "x".repeat(100);
        String text = IntStream.rangeClosed(1, 40)
                .mapToObj(i -> longLine)
                .collect(Collectors.joining("\n"));

        List<TextSegment> result = splitter.fallbackSplit(text, new Metadata());

        assertFalse(result.isEmpty());
        for (TextSegment segment : result) {
            assertTrue(segment.text().length() <= 3000, "Chunk must not exceed 3000 chars");
        }
    }
}
