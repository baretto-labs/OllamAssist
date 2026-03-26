package fr.baretto.ollamassist.chat.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RRFFusionTest {

    private static EmbeddingMatch<TextSegment> match(String id) {
        return new EmbeddingMatch<>(1.0, id, null, TextSegment.from(id));
    }

    @Test
    void fuse_emptyLists_returnsEmpty() {
        List<EmbeddingMatch<TextSegment>> result = RRFFusion.fuse(List.of(), List.of(), 5);
        assertTrue(result.isEmpty());
    }

    @Test
    void fuse_oneEmptyList_returnsSingleListResults() {
        List<EmbeddingMatch<TextSegment>> knn = List.of(match("a"), match("b"));
        List<EmbeddingMatch<TextSegment>> result = RRFFusion.fuse(knn, List.of(), 5);

        assertEquals(2, result.size());
        assertEquals("a", result.get(0).embeddingId());
        assertEquals("b", result.get(1).embeddingId());
    }

    @Test
    void fuse_documentInBothLists_hasHigherScore() {
        // "shared" appears at rank 0 in both lists
        List<EmbeddingMatch<TextSegment>> knn = List.of(match("shared"), match("knn-only"));
        List<EmbeddingMatch<TextSegment>> bm25 = List.of(match("shared"), match("bm25-only"));

        List<EmbeddingMatch<TextSegment>> result = RRFFusion.fuse(knn, bm25, 3);

        EmbeddingMatch<TextSegment> sharedMatch = result.stream()
                .filter(m -> "shared".equals(m.embeddingId()))
                .findFirst()
                .orElseThrow();

        double expectedScore = 2.0 / (RRFFusion.RRF_K + 1);
        assertEquals(expectedScore, sharedMatch.score(), 1e-10);
        assertEquals("shared", result.get(0).embeddingId(), "shared should rank first");
    }

    @Test
    void fuse_deduplicatesOnEmbeddingId() {
        List<EmbeddingMatch<TextSegment>> knn = List.of(match("dup"), match("unique-knn"));
        List<EmbeddingMatch<TextSegment>> bm25 = List.of(match("dup"), match("unique-bm25"));

        List<EmbeddingMatch<TextSegment>> result = RRFFusion.fuse(knn, bm25, 10);

        long dupCount = result.stream().filter(m -> "dup".equals(m.embeddingId())).count();
        assertEquals(1, dupCount, "Duplicate ID must appear only once");
        assertEquals(3, result.size());
    }

    @Test
    void fuse_respectsTopK() {
        List<EmbeddingMatch<TextSegment>> knn = List.of(match("a"), match("b"), match("c"), match("d"));
        List<EmbeddingMatch<TextSegment>> bm25 = List.of(match("e"), match("f"));

        List<EmbeddingMatch<TextSegment>> result = RRFFusion.fuse(knn, bm25, 3);

        assertEquals(3, result.size());
    }

    @Test
    void fuse_rrfScoreFormula_isCorrect() {
        // Single item at rank 0 in knn only: score = 1 / (60 + 0 + 1) = 1/61
        List<EmbeddingMatch<TextSegment>> knn = List.of(match("x"));
        List<EmbeddingMatch<TextSegment>> result = RRFFusion.fuse(knn, List.of(), 1);

        double expected = 1.0 / (RRFFusion.RRF_K + 1);
        assertEquals(expected, result.get(0).score(), 1e-10);
    }

    @Test
    void fuse_resultsSortedByScoreDescending() {
        // rank 0 in knn → score 1/61, rank 0 in bm25 → score 1/61, combined → 2/61
        // "knn-only" at rank 1 in knn → score 1/62
        List<EmbeddingMatch<TextSegment>> knn = List.of(match("top"), match("mid"));
        List<EmbeddingMatch<TextSegment>> bm25 = List.of(match("top"), match("low"));

        List<EmbeddingMatch<TextSegment>> result = RRFFusion.fuse(knn, bm25, 10);

        for (int i = 0; i < result.size() - 1; i++) {
            assertTrue(result.get(i).score() >= result.get(i + 1).score(),
                    "Results must be sorted by score descending");
        }
    }

    @Test
    void fuse_topKExceedsResults_returnsAll() {
        List<EmbeddingMatch<TextSegment>> knn = List.of(match("a"));
        List<EmbeddingMatch<TextSegment>> bm25 = List.of(match("b"));

        List<EmbeddingMatch<TextSegment>> result = RRFFusion.fuse(knn, bm25, 100);

        assertEquals(2, result.size());
    }
}
