package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.project.Project;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static fr.baretto.ollamassist.chat.rag.IndexRegistry.OLLAMASSIST_DIR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BM25 search runs against whatever lucene-core the IDE ships: Lucene 9 up to 2025.2,
 * Lucene 10 from 2025.3. These tests run on the 2024.3 platform, so they catch any
 * dependency compiled against the other major version.
 */
class LuceneEmbeddingStoreBm25SearchTest {

    private String projectName;
    private LuceneEmbeddingStore<TextSegment> store;

    @BeforeEach
    void setUp() throws IOException {
        projectName = "bm25-search-test-" + UUID.randomUUID();
        Project project = mock(Project.class);
        when(project.getName()).thenReturn(projectName);
        store = new LuceneEmbeddingStore<>(project);
    }

    @AfterEach
    void tearDown() throws IOException {
        store.close();
        Path projectDir = Paths.get(OLLAMASSIST_DIR, projectName);
        if (Files.exists(projectDir)) {
            try (Stream<Path> paths = Files.walk(projectDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test
    void shouldFindSegmentMatchingAnyWordOfAMultiWordQuery() {
        store.add(embedding(), TextSegment.from("The hybrid retriever fuses BM25 and vector results."));
        store.add(embedding(), TextSegment.from("Inline completion is debounced by 300 ms."));

        List<EmbeddingMatch<TextSegment>> matches = store.bm25Search("why did hybrid retrieval fail", 5);

        assertThat(matches)
                .extracting(match -> match.embedded().text())
                .containsExactly("The hybrid retriever fuses BM25 and vector results.");
    }

    @Test
    void shouldTreatQuerySyntaxInTheUserTextAsPlainWords() {
        store.add(embedding(), TextSegment.from("The hybrid retriever fuses BM25 and vector results."));

        List<EmbeddingMatch<TextSegment>> matches = store.bm25Search("hybrid AND -retriever OR \"(vector:*", 5);

        assertThat(matches).hasSize(1);
    }

    @Test
    void shouldReturnNoMatchWhenTheQueryHasNoSearchableWord() {
        store.add(embedding(), TextSegment.from("The hybrid retriever fuses BM25 and vector results."));

        List<EmbeddingMatch<TextSegment>> matches = store.bm25Search("?! -- ()", 5);

        assertThat(matches).isEmpty();
    }

    private static Embedding embedding() {
        return Embedding.from(new float[]{0.1f, 0.2f, 0.3f});
    }
}
