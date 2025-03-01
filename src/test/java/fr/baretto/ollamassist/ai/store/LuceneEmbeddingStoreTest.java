package fr.baretto.ollamassist.ai.store;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModelFactory;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import fr.baretto.ollamassist.chat.rag.LuceneEmbeddingStore;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LuceneEmbeddingStoreTest {

    private LuceneEmbeddingStore<TextSegment> store;
    private static final BgeSmallEnV15QuantizedEmbeddingModelFactory EMBEDDING_FACTORY = new BgeSmallEnV15QuantizedEmbeddingModelFactory();
    private OllamAssistSettings settings = Mockito.mock(OllamAssistSettings.class);

    @BeforeEach
    void setUp() throws Exception {
        store = new LuceneEmbeddingStore<>(new DummyProject());
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.removeAll();
            store.close();
        }
    }

    @Test
    void testIndexAndSearchDocumentsUsingFilesUtil() throws IOException {
        try (MockedStatic<OllamAssistSettings> ollamAssistSettingsMocked = Mockito.mockStatic(OllamAssistSettings.class)) {
            ollamAssistSettingsMocked.
                    when(OllamAssistSettings::getInstance)
                    .thenReturn(settings);
            Mockito.doReturn("data").when(settings).getSources();


            Document doc1 = FileSystemDocumentLoader.loadDocument(Path.of(getResourceFilePath("FilesUtil.java")));
            Document doc2 = FileSystemDocumentLoader.loadDocument(Path.of(getResourceFilePath("README.adoc")));

            List<Document> documents = List.of(doc1, doc2);
            EmbeddingStoreIngestor.ingest(documents, store);


            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embed("BATCH_SIZE"))
                    .maxResults(1)
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = store.search(searchRequest);

            assertNotNull(searchResult);
            assertEquals(1, searchResult.matches().size());
            assertEquals("FilesUtil.java", searchResult.matches().get(0).embeddingId());


            searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embed("LICENSE-2.0"))
                    .maxResults(1)
                    .build();

            searchResult = store.search(searchRequest);

            assertNotNull(searchResult);
            assertEquals(1, searchResult.matches().size());
            assertEquals("README.adoc", searchResult.matches().get(0).embeddingId());
        }
    }

    private @NotNull Embedding embed(String string) {
        return EMBEDDING_FACTORY.create().embed(string).content();
    }

    public static String getResourceFilePath(String filename) {
        URL url = Thread.currentThread().getContextClassLoader()
                .getResource("data/" + filename);

        if (url != null && url.getProtocol().equals("jar")) {
            return url.toString();
        }

        if (url != null) {
            return url.getPath();
        }

        return Path.of("src/test/resources/data", filename)
                .toAbsolutePath().toString();
    }
}