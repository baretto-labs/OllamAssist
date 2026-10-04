package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.project.Project;
import dev.langchain4j.data.document.Metadata;
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
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static fr.baretto.ollamassist.chat.rag.IndexRegistry.OLLAMASSIST_DIR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The store keeps track of which file each segment comes from, so a single file can be
 * removed or re-indexed without rebuilding the whole KnowledgeIndex.
 */
class LuceneEmbeddingStoreFileTrackingTest {

    private String projectName;
    private LuceneEmbeddingStore<TextSegment> store;

    @BeforeEach
    void setUp() throws IOException {
        projectName = "file-tracking-test-" + UUID.randomUUID();
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
    void removingAFileRemovesEverySegmentOfThatFileOnly() {
        index("/repo/src", "Foo.java", "retriever segment one");
        index("/repo/src", "Foo.java", "retriever segment two");
        index("/repo/src", "Bar.java", "retriever segment three");

        store.removeAll(new IdStartWithFilter("/repo/src/Foo.java"));

        assertThat(texts(store.bm25Search("retriever", 10))).containsExactly("retriever segment three");
    }

    @Test
    void removingAFileKeepsAFileWhosePathStartsTheSame() {
        index("/repo/src", "Foo.java", "retriever segment one");
        index("/repo/src", "Foo.java.orig", "retriever segment two");

        store.removeAll(new IdStartWithFilter("/repo/src/Foo.java"));

        assertThat(texts(store.bm25Search("retriever", 10))).containsExactly("retriever segment two");
    }

    @Test
    void aFileIndexedWithAWindowsPathIsRemovedByItsIdePath() {
        index("C:\\repo\\src", "Foo.java", "retriever segment one");

        store.removeAll(new IdStartWithFilter("C:/repo/src/Foo.java"));

        assertThat(store.bm25Search("retriever", 10)).isEmpty();
    }

    @Test
    void listsEachIndexedFileOnceWithTheTimeItWasIndexed() {
        Instant before = Instant.now();
        index("/repo/src", "Foo.java", "retriever segment one");
        index("/repo/src", "Foo.java", "retriever segment two");
        index("/repo/src", "Bar.java", "retriever segment three");
        Instant after = Instant.now();

        Map<String, Instant> indexedFiles = store.indexedFiles();

        assertThat(indexedFiles).containsOnlyKeys("/repo/src/Foo.java", "/repo/src/Bar.java");
        assertThat(indexedFiles.values()).allSatisfy(indexedAt -> assertThat(indexedAt).isBetween(before, after));
    }

    @Test
    void anIndexThatWasNeverWrittenToContainsNoFile() {
        assertThat(store.indexedFiles()).isEmpty();
    }

    private void index(String directory, String fileName, String text) {
        Metadata metadata = Metadata.from(Map.of("absolute_directory_path", directory, "file_name", fileName));
        store.add(Embedding.from(new float[]{0.1f, 0.2f, 0.3f}), TextSegment.from(text, metadata));
    }

    private static List<String> texts(List<EmbeddingMatch<TextSegment>> matches) {
        return matches.stream().map(match -> match.embedded().text()).toList();
    }
}
