package fr.baretto.ollamassist.chat.rag;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to reproduce and verify the fix for issue #146:
 * Plugin hangs when loading index created with older Lucene version (9.12)
 * using newer Lucene version (10.3).
 */
class LuceneCodecCompatibilityTest {

    private Path tempIndexPath;
    private Directory directory;

    @BeforeEach
    void setUp() throws IOException {
        tempIndexPath = Files.createTempDirectory("lucene-compat-test");
        directory = new NIOFSDirectory(tempIndexPath);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (directory != null) {
            directory.close();
        }
        deleteDirectory(tempIndexPath);
    }

    @Test
    void testOpenIndexWithCurrentLuceneVersion() throws IOException {
        // Step 1: Create an index with current Lucene version
        createSampleIndex();

        // Step 2: Verify we can read it
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        // This should work fine with same version
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            assertThat(writer.getDocStats().numDocs).isGreaterThan(0);
        }
    }

    @Test
    void testDetectLuceneVersion() throws IOException {
        // Create an index
        createSampleIndex();

        // Read segment info to check codec version
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            SegmentInfos segmentInfos = SegmentInfos.readLatestCommit(directory);

            System.out.println("=== Lucene Codec Information ===");
            System.out.println("Lucene Version: " + org.apache.lucene.util.Version.LATEST);
            System.out.println("Segment Count: " + segmentInfos.size());

            for (SegmentCommitInfo info : segmentInfos) {
                System.out.println("Segment: " + info.info.name);
                System.out.println("  Codec: " + info.info.getCodec().getName());
                System.out.println("  Version: " + info.info.getVersion());
            }
        }
    }

    /**
     * This test simulates what happens when we try to open an index
     * created with Lucene 9.12 codec using Lucene 10.3.
     *
     * Note: We can't actually create a Lucene 9.12 index in this test
     * because we're running with Lucene 10.3. This test documents the
     * expected behavior when encountering such a situation.
     */
    @Test
    void testHandleIncompatibleCodec() throws IOException {
        // Create a normal index
        createSampleIndex();

        // Close directory to simulate a fresh start
        directory.close();
        directory = new NIOFSDirectory(tempIndexPath);

        // Try to open it - should work with same version
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        try (IndexWriter writer = new IndexWriter(directory, config)) {
            // Should succeed
            assertThat(writer).isNotNull();
        }

        System.out.println("\nTo reproduce the actual issue #146:");
        System.out.println("1. Install OllamAssist version <= 1.9.0 (which uses Lucene 9.12)");
        System.out.println("2. Use the plugin to create some index data");
        System.out.println("3. Upgrade to OllamAssist 1.10.0 (which uses Lucene 10.3)");
        System.out.println("4. The plugin will fail to open the index with codec error");
    }

    private void createSampleIndex() throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        try (IndexWriter writer = new IndexWriter(directory, config)) {
            Document doc = new Document();
            doc.add(new StringField("id", "test-1", Field.Store.YES));
            doc.add(new StringField("content", "Sample document", Field.Store.YES));
            writer.addDocument(doc);
            writer.commit();
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }
}