package fr.baretto.ollamassist.chat.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import fr.baretto.ollamassist.events.StoreNotifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.store.SingleInstanceLockFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import static fr.baretto.ollamassist.chat.rag.IndexRegistry.OLLAMASSIST_DIR;

@Slf4j
public final class LuceneEmbeddingStore<EMBEDDED> implements EmbeddingStore<EMBEDDED>, Closeable, Disposable {

    public static final String DATABASE_KNOWLEDGE_INDEX = "/database/knowledge_index/";
    private static final String PATH_SEPARATOR = "/";
    private static final String FILE_NOT_FOUND_FORMAT = "File not found for id: %s";
    private static final String FILE_READ_ERROR_FORMAT = "Failed to read file content for: %s";
    private static final String VECTOR = "vector";
    private static final String EMBEDDED = "embedded";
    private static final String LAST_INDEXED_DATE = "last_indexed_date";
    private static final String METADATA = "metadata";
    private static final String ID = "id";


    private final Directory directory;
    private final StandardAnalyzer analyzer;
    private final ObjectMapper mapper;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Project project;
    private IndexWriter indexWriter;

    public LuceneEmbeddingStore(Project project) throws IOException {
        this.directory = new NIOFSDirectory(
                Paths.get(OLLAMASSIST_DIR, project.getName(), DATABASE_KNOWLEDGE_INDEX),
                new SingleInstanceLockFactory()
        );
        this.analyzer = new StandardAnalyzer();
        this.mapper = new ObjectMapper();
        this.indexWriter = retrieveIndexWriter();
        this.project = project;
    }

    private void initIndexWriter() throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        this.indexWriter = new IndexWriter(directory, config);
    }

    private synchronized IndexWriter retrieveIndexWriter() throws IOException {
        if (indexWriter == null || !indexWriter.isOpen()) {
            closeIndexWriter();
            initIndexWriter();
        }
        return indexWriter;
    }

    public void closeIndexWriter() {
        rwLock.writeLock().lock();
        try {
            if (indexWriter != null) {
                log.debug("Closing IndexWriter...");
                indexWriter.close();
                indexWriter = null;
            }
        } catch (IOException e) {
            log.error("Error closing Lucene IndexWriter", e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }


    @Override
    public String add(Embedding embedding) {
        rwLock.writeLock().lock();
        try {
            String id = getUniqueId(null, UUID.randomUUID().toString());
            add(id, embedding, null);
            return id;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void add(String id, Embedding embedding) {
        add(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, EMBEDDED embedded) {
        rwLock.writeLock().lock();
        try {
            String id = getUniqueId(embedded, UUID.randomUUID().toString());
            add(id, embedding, embedded);
            return id;
        } catch (Exception exception) {
            throw new CorruptedIndexException();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void add(String id, Embedding embedding, EMBEDDED embedded) {
        rwLock.writeLock().lock();
        try {
            if (indexWriter == null) {
                indexWriter = retrieveIndexWriter();
            }
            indexWriter.updateDocument(new Term(ID, id), toDocument(embedding, embedded, id));
            indexWriter.commit();
        } catch (Exception e) {
            throw new CorruptedIndexException();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private Document toDocument(Embedding embedding, EMBEDDED embedded, String id) {
        Document doc = new Document();

        doc.add(new StringField(ID, id, Field.Store.YES));
        doc.add(new StoredField(EMBEDDED, ((TextSegment) embedded).text()));

        String lastIndexedDate = ZonedDateTime.now().toString();
        doc.add(new StringField(LAST_INDEXED_DATE, lastIndexedDate, Field.Store.YES));

        String metadata = null;
        try {
            metadata = mapper.writeValueAsString(((TextSegment) embedded).metadata().toMap());
        } catch (JsonProcessingException e) {
            metadata = "";
        }
        doc.add(new StoredField(METADATA, metadata));

        float[] vector = embedding.vector();
        FieldType vectorFieldType = KnnFloatVectorField.createFieldType(vector.length, VectorSimilarityFunction.COSINE);
        doc.add(new KnnFloatVectorField(VECTOR, vector, vectorFieldType));

        return doc;
    }

    public List<String> addAll(List<Embedding> embeddings) {
        return addAll(embeddings, Collections.emptyList());
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<EMBEDDED> metadataList) {
        rwLock.writeLock().lock();
        try {
            List<Document> documents = new ArrayList<>(embeddings.size());
            List<String> ids = new ArrayList<>(embeddings.size());

            for (int i = 0; i < embeddings.size(); i++) {
                EMBEDDED embedded = i < metadataList.size() ? metadataList.get(i) : null;
                String id = getUniqueId(embedded, UUID.randomUUID().toString());
                ids.add(id);

                documents.add(createDocument(
                        embeddings.get(i),
                        embedded,
                        id
                ));
            }
            if (indexWriter == null) {
                indexWriter = retrieveIndexWriter();
            }
            indexWriter.addDocuments(documents);
            indexWriter.commit();
            return ids;
        } catch (Exception exception) {
            throw new CorruptedIndexException();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void removeAll() {
        rwLock.writeLock().lock();
        try {
            retrieveIndexWriter();
            Query query = new MatchAllDocsQuery();
            indexWriter.deleteDocuments(query);
            indexWriter.commit();
        } catch (IOException e) {
            log.error("Failed to remove all documents, resetting IndexWriter", e);
            recreateIndex();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        rwLock.writeLock().lock();
        try {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            for (String id : ids) {
                builder.add(new TermQuery(new Term(ID, id)), BooleanClause.Occur.SHOULD);
            }
            if (indexWriter == null) {
                indexWriter = retrieveIndexWriter();
            }
            indexWriter.deleteDocuments(builder.build());
            indexWriter.commit();
        } catch (IOException e) {
            log.error("Failed to remove documents with specified IDs", e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void removeAll(Filter filter) {
        rwLock.writeLock().lock();
        try {
            if (filter instanceof IdStartWithFilter idStartWithFilter) {
                if (indexWriter == null) {
                    indexWriter = retrieveIndexWriter();
                }
                indexWriter.deleteDocuments(idStartWithFilter.toLuceneQuery());
                indexWriter.commit();
            } else {
                throw new UnsupportedOperationException("Filter type not supported: " + filter.getClass());
            }
        } catch (IOException e) {
            log.error("Failed to remove documents matching the filter", e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public EmbeddingSearchResult<EMBEDDED> search(EmbeddingSearchRequest request) {
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            float[] queryVector = request.queryEmbedding().vector();
            Query vectorQuery = KnnFloatVectorField.newVectorQuery(VECTOR, queryVector, request.maxResults());

            TopDocs topDocs;
            try {
                topDocs = searcher.search(vectorQuery, request.maxResults());
            } catch (Exception exception) {
                recreateIndex();
                project.getMessageBus()
                        .syncPublisher(StoreNotifier.TOPIC)
                        .clearDatabaseAndRunIndexation();

                return new EmbeddingSearchResult<>(List.of());
            }

            double[] scores = Arrays.stream(topDocs.scoreDocs)
                    .mapToDouble(sd -> sd.score)
                    .toArray();
            double dynamicThreshold = calculateDynamicThreshold(
                    scores,
                    request.minScore(),
                    topDocs.scoreDocs[0].score
            );

            List<EmbeddingMatch<EMBEDDED>> matches = new ArrayList<>();

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);

                String id = doc.get(ID);
                String lastIndexedDate = doc.get(LAST_INDEXED_DATE);
                String embeddedText = doc.get(EMBEDDED);

                Metadata metadata = new Metadata(mapper.readValue(doc.get(METADATA), Map.class));
                metadata.put(LAST_INDEXED_DATE, lastIndexedDate);

                if (scoreDoc.score > dynamicThreshold) {
                    matches.add(new EmbeddingMatch<>((double) scoreDoc.score, id, null, (EMBEDDED) TextSegment.from(embeddedText, metadata)));
                }
            }
            return new EmbeddingSearchResult<>(matches);
        } catch (Exception e) {
            log.error("Exception during lucene embedding request", e);
            return new EmbeddingSearchResult<>(List.of());
        }
    }

    private String readFileContentFromId(String path) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        if (file == null || !file.exists()) {
            log.warn(String.format(FILE_NOT_FOUND_FORMAT, path));
            return null;
        }

        try {
            return VfsUtilCore.loadText(file);
        } catch (IOException e) {
            log.error(String.format(FILE_READ_ERROR_FORMAT, path), e);
            return null;
        }
    }

    /**
     * Computes dynamic relevance threshold for vector search results.
     *
     * <p>Implements hybrid strategy:
     * 1. <b>Range-based</b>: Keeps top 70% of [baseMinScore, maxScore] interval
     * 2. <b>Statistical fallback</b>: For flat distributions (range < 0.1), uses average - 0.05 margin
     * 3. <b>Safety</b>: Always respects baseMinScore as absolute floor
     *
     * @param scores       Raw similarity scores of candidate documents
     * @param baseMinScore Absolute minimum relevance threshold
     * @param maxScore     Highest similarity score in current results
     * @return Dynamic threshold ≥ baseMinScore
     */
    private double calculateDynamicThreshold(double[] scores, double baseMinScore, double maxScore) {
        double scoreRange = maxScore - baseMinScore;
        double dynamicThreshold = maxScore - (0.3 * scoreRange);

        if (scoreRange < 0.1) {
            double avg = Arrays.stream(scores).average().orElse(baseMinScore);
            dynamicThreshold = Math.max(baseMinScore, avg - 0.05);
        }

        return Math.max(baseMinScore, dynamicThreshold);
    }

    @Override
    public void close() {
        rwLock.writeLock().lock();
        try {
            closeIndexWriter();
            directory.close();
        } catch (IOException e) {
            log.error("Error closing Lucene directory", e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void recreateIndex() {
        try {
            log.info("Recreating index...");
            closeIndexWriter();
            deleteAllIndexFiles();
            initIndexWriter();
            log.info("Index recreated successfully");
        } catch (IOException e) {
            log.error("Échec de la recréation de l'index", e);
        }
    }

    private void deleteAllIndexFiles() throws IOException {
        closeIndexWriter();
        Path indexPath = Paths.get(OLLAMASSIST_DIR, project.getName(), DATABASE_KNOWLEDGE_INDEX);
        cleanDirectory(indexPath);
    }

    private void cleanDirectory(Path directoryPath) throws IOException {
        if (!Files.isDirectory(directoryPath)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(directoryPath)) {
            stream
                    .skip(1)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }


    @Override
    public void dispose() {
        close();
    }

    private String getUniqueId(EMBEDDED embedded, String defaultId) {

        if (embedded instanceof TextSegment textSegment) {
            try {
                return String.format("%s%s%s%s",
                    textSegment.metadata().getString("absolute_directory_path"),
                    PATH_SEPARATOR,
                    textSegment.metadata().getString("file_name"),
                    UUID.randomUUID());
            } catch (Exception exception) {
                return defaultId;
            }

        }
        return defaultId;
    }

    private Document createDocument(Embedding embedding, EMBEDDED embedded, String filePath) {
        Document doc = new Document();

        doc.add(new StringField(ID, filePath, Field.Store.YES));

        if (embedded instanceof TextSegment segment) {
            doc.add(new StoredField(EMBEDDED, segment.text()));

            String lastIndexedDate = ZonedDateTime.now().toString();
            doc.add(new StringField(LAST_INDEXED_DATE, lastIndexedDate, Field.Store.YES));

            String metadata = serializeMetadata(segment.metadata());
            doc.add(new StoredField(METADATA, metadata));
        }

        float[] vector = embedding.vector();
        FieldType vectorType = KnnFloatVectorField.createFieldType(vector.length, VectorSimilarityFunction.COSINE);
        doc.add(new KnnFloatVectorField(VECTOR, vector, vectorType));

        return doc;
    }

    private String serializeMetadata(Metadata metadata) {
        try {
            return mapper.writeValueAsString(metadata.toMap());
        } catch (JsonProcessingException e) {
            log.error("Metadata serialization failed", e);
            return "{}";
        }
    }

}
