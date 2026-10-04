package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.project.Project;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
 * Indexing a project that already has an index must not add its files a second time: a stale
 * index (older than seven days) used to be re-indexed on top of itself, duplicating every segment.
 */
class IndexSynchronizerTest {

    @TempDir
    Path workspace;

    private String projectName;
    private LuceneEmbeddingStore<TextSegment> store;
    private List<String> queued;
    private IndexSynchronizer synchronizer;

    @BeforeEach
    void setUp() throws IOException {
        projectName = "index-synchronizer-test-" + UUID.randomUUID();
        Project project = mock(Project.class);
        when(project.getName()).thenReturn(projectName);
        store = new LuceneEmbeddingStore<>(project);
        queued = new ArrayList<>();
        synchronizer = new IndexSynchronizer(store, queued::addAll);
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
    void aFileAlreadyIndexedAndUnchangedIsNotIndexedAgain() throws IOException {
        Path file = fileModifiedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        index(file);

        synchronizer.synchronize(List.of(ideaPath(file)));

        assertThat(queued).isEmpty();
        assertThat(store.indexedFiles()).containsOnlyKeys(ideaPath(file));
    }

    @Test
    void aFileModifiedSinceItWasIndexedLosesItsOldSegmentsAndIsQueued() throws IOException {
        Path file = fileModifiedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        index(file);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plus(1, ChronoUnit.MINUTES)));

        synchronizer.synchronize(List.of(ideaPath(file)));

        assertThat(queued).containsExactly(ideaPath(file));
        assertThat(store.indexedFiles()).isEmpty();
    }

    @Test
    void anEmptyIndexQueuesEveryFile() throws IOException {
        Path first = fileModifiedAt(Instant.now());
        Path second = Files.writeString(workspace.resolve("Second.java"), "class Second {}");

        synchronizer.synchronize(List.of(ideaPath(first), ideaPath(second)));

        assertThat(queued).containsExactlyInAnyOrder(ideaPath(first), ideaPath(second));
    }

    private Path fileModifiedAt(Instant modifiedAt) throws IOException {
        Path file = Files.writeString(workspace.resolve("Foo.java"), "class Foo {}");
        Files.setLastModifiedTime(file, FileTime.from(modifiedAt));
        return file;
    }

    private void index(Path file) {
        Metadata metadata = Metadata.from(Map.of(
                "absolute_directory_path", file.getParent().toString(),
                "file_name", file.getFileName().toString()));
        store.add(Embedding.from(new float[]{0.1f, 0.2f, 0.3f}), TextSegment.from("class Foo {}", metadata));
    }

    private static String ideaPath(Path file) {
        return file.toString().replace('\\', '/');
    }
}
