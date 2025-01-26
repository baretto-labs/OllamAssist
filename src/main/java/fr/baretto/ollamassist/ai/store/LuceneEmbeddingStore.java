package fr.baretto.ollamassist.ai.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.Disposable;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.store.NoLockFactory;
import org.apache.lucene.store.SingleInstanceLockFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static fr.baretto.ollamassist.ai.store.IndexRegistry.OLLAMASSIST_DIR;

public class LuceneEmbeddingStore<Embedded> implements EmbeddingStore<Embedded>, Closeable, Disposable {

    public static final String DATABASE_KNOWLEDGE_INDEX = "/database/knowledge_index/";
    private final Directory directory;
    private final StandardAnalyzer analyzer;
    private final ObjectMapper mapper;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private IndexWriter indexWriter;

    public LuceneEmbeddingStore() throws IOException {
        this.directory = new NIOFSDirectory(
                Paths.get(OLLAMASSIST_DIR + DATABASE_KNOWLEDGE_INDEX),
                new SingleInstanceLockFactory()
        );

        this.analyzer = new StandardAnalyzer();
        this.mapper = new ObjectMapper();
        this.indexWriter = createIndexWriter();
    }

    private synchronized IndexWriter createIndexWriter() throws IOException {
        if (indexWriter == null) {
            indexWriter = new IndexWriter(directory, new IndexWriterConfig(analyzer));
        }
        return indexWriter;
    }

    public synchronized void closeIndexWriter() {
        try {
            if (indexWriter != null) {
                indexWriter.close();
                indexWriter = null;
            }
        } catch (IOException e) {
            throw new RuntimeException("Error closing Lucene IndexWriter", e);
        }
    }

    @Override
    public String add(Embedding embedding) {
        rwLock.writeLock().lock();
        try {
            String id = UUID.randomUUID().toString();
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
    public String add(Embedding embedding, Embedded embedded) {
        rwLock.writeLock().lock();
        try {
            String id = UUID.randomUUID().toString();
            add(id, embedding, embedded);
            return id;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void add(String id, Embedding embedding, Embedded embedded) {
        rwLock.writeLock().lock();
        try {
            Document doc = new Document();

            // Metadata
            String fileName = Optional.ofNullable(((TextSegment) embedded).metadata().getString("file_name"))
                    .orElse(id);

            String projectId = Optional.ofNullable(((TextSegment) embedded).metadata().getString("project_id"))
                    .orElse("default");

            // Add fields to the document
            doc.add(new StringField("id", fileName, Field.Store.YES));
            doc.add(new StringField("projectId", projectId, Field.Store.YES));
            doc.add(new StoredField("embedded", ((TextSegment) embedded).text()));

            // Add last_indexed_date field
            String lastIndexedDate = ZonedDateTime.now().toString();
            doc.add(new StringField("last_indexed_date", lastIndexedDate, Field.Store.YES));

            // Add metadata as JSON
            String metadata = mapper.writeValueAsString(((TextSegment) embedded).metadata().toMap());
            doc.add(new StoredField("metadata", metadata));

            // Add vector
            float[] vector = embedding.vector();
            FieldType vectorFieldType = KnnFloatVectorField.createFieldType(vector.length, VectorSimilarityFunction.COSINE);
            doc.add(new KnnFloatVectorField("vector", vector, vectorFieldType));

            // Index or update the document
            indexWriter.updateDocument(new Term("id", fileName), doc);
            indexWriter.commit();
        } catch (IOException e) {
            throw new RuntimeException("Error adding or updating document in Lucene", e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        rwLock.writeLock().lock();
        try {
            List<String> ids = new ArrayList<>();
            for (Embedding embedding : embeddings) {
                ids.add(add(embedding));
            }
            return ids;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<Embedded> metadataList) {
        rwLock.writeLock().lock();
        try {
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < embeddings.size(); i++) {
                Embedded metadata = (i < metadataList.size()) ? metadataList.get(i) : null;
                ids.add(add(embeddings.get(i), metadata));
            }
            return ids;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void removeAll() {
        rwLock.writeLock().lock();
        try {
            Query query = new MatchAllDocsQuery();
            indexWriter.deleteDocuments(query);
            indexWriter.commit();
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove all documents", e);
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
                builder.add(new TermQuery(new Term("id", id)), BooleanClause.Occur.SHOULD);
            }
            indexWriter.deleteDocuments(builder.build());
            indexWriter.commit();
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove documents with specified IDs", e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void removeAll(Filter filter) {
        rwLock.writeLock().lock();
        try {
            if (filter instanceof IdEqualsFilter idEqualsFilter) {
                indexWriter.deleteDocuments(idEqualsFilter.toLuceneQuery());
                indexWriter.commit();
            } else {
                throw new UnsupportedOperationException("Filter type not supported: " + filter.getClass());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove documents matching the filter", e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public EmbeddingSearchResult<Embedded> search(EmbeddingSearchRequest request) {
        rwLock.readLock().lock();
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            float[] queryVector = request.queryEmbedding().vector();
            Query vectorQuery = KnnFloatVectorField.newVectorQuery("vector", queryVector, request.maxResults());

            TopDocs topDocs = searcher.search(vectorQuery, request.maxResults());

            List<EmbeddingMatch<Embedded>> matches = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);

                String id = doc.get("id");
                String lastIndexedDate = doc.get("last_indexed_date");
                String embeddedText = doc.get("embedded");

                Metadata metadata = new Metadata(mapper.readValue(doc.get("metadata"), Map.class));
                metadata.put("last_indexed_date", lastIndexedDate);

                Embedded textSegment = (Embedded) TextSegment.from(embeddedText, metadata);

                matches.add(new EmbeddingMatch<>((double) scoreDoc.score, id, null, textSegment));
            }

            return new EmbeddingSearchResult<>(matches);
        } catch (IOException e) {
            throw new RuntimeException("Error searching Lucene index", e);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        rwLock.writeLock().lock();
        try {
            closeIndexWriter();
            directory.close();
        } catch (IOException e) {
            throw new RuntimeException("Error closing Lucene directory", e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void dispose() {
        close();
    }
}
