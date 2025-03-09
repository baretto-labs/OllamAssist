package fr.baretto.ollamassist.chat.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
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
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static fr.baretto.ollamassist.chat.rag.IndexRegistry.OLLAMASSIST_DIR;

@Slf4j
public final class LuceneEmbeddingStore<Embedded> implements EmbeddingStore<Embedded>, Closeable, Disposable {

    public static final String DATABASE_KNOWLEDGE_INDEX = "/database/knowledge_index/";
    public static final String FILE_PATH = "file_path";
    private final Directory directory;
    private final StandardAnalyzer analyzer;
    private final ObjectMapper mapper;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private IndexWriter indexWriter;

    public LuceneEmbeddingStore(Project project) throws IOException {
        this.directory = new NIOFSDirectory(
                Paths.get(OLLAMASSIST_DIR, project.getName(), DATABASE_KNOWLEDGE_INDEX),
                new SingleInstanceLockFactory()
        );
        this.analyzer = new StandardAnalyzer();
        this.mapper = new ObjectMapper();
        this.indexWriter = retrieveIndexWriter();
    }

    private synchronized IndexWriter retrieveIndexWriter() throws IOException {
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
    public String add(Embedding embedding, Embedded embedded) {
        rwLock.writeLock().lock();
        try {
            String id = getUniqueId(embedded, UUID.randomUUID().toString());
            add(id, embedding, embedded);
            return id;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void add(String id, Embedding embedding, Embedded embedded) {
        rwLock.writeLock().lock();
        try {
            if (indexWriter == null) {
                indexWriter = retrieveIndexWriter();
            }
            indexWriter.updateDocument(new Term("id", id), toDocument(embedding, embedded, id));
            indexWriter.commit();
        } catch (IOException e) {
            throw new RuntimeException("Error adding or updating document in Lucene", e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private Document toDocument(Embedding embedding, Embedded embedded, String id) {
        Document doc = new Document();

        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new StoredField("embedded", ((TextSegment) embedded).text()));

        String lastIndexedDate = ZonedDateTime.now().toString();
        doc.add(new StringField("last_indexed_date", lastIndexedDate, Field.Store.YES));

        String metadata = null;
        try {
            metadata = mapper.writeValueAsString(((TextSegment) embedded).metadata().toMap());
        } catch (JsonProcessingException e) {
            metadata = "";
        }
        doc.add(new StoredField("metadata", metadata));

        float[] vector = embedding.vector();
        FieldType vectorFieldType = KnnFloatVectorField.createFieldType(vector.length, VectorSimilarityFunction.COSINE);
        doc.add(new KnnFloatVectorField("vector", vector, vectorFieldType));

        return doc;
    }

    public List<String> addAll(List<Embedding> embeddings) {
        return addAll(embeddings, Collections.emptyList());
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<Embedded> metadataList) {
        rwLock.writeLock().lock();
        try {
            List<Document> documents = new ArrayList<>(embeddings.size());
            List<String> ids = new ArrayList<>(embeddings.size());

            for (int i = 0; i < embeddings.size(); i++) {
                Embedded embedded = i < metadataList.size() ? metadataList.get(i) : null;
                String id = getUniqueId(embedded, UUID.randomUUID().toString());
                ids.add(id);

                documents.add(createDocument(
                        embeddings.get(i),
                        embedded,
                        id,
                        getProjectId(embedded)
                ));
            }
            if (indexWriter == null) {
                indexWriter = retrieveIndexWriter();
            }
            indexWriter.addDocuments(documents);
            indexWriter.commit();
            return ids;
        } catch (IOException e) {
            throw new RuntimeException("Bulk add operation failed", e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void removeAll() {
        rwLock.writeLock().lock();
        try {
            if (indexWriter == null) {
                indexWriter = retrieveIndexWriter();
            }
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
            if (indexWriter == null) {
                indexWriter = retrieveIndexWriter();
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

    private String getUniqueId(Embedded embedded, String defaultId) {

        if (embedded instanceof TextSegment textSegment) {
            try{
                return textSegment.metadata().getString("absolute_directory_path") + "/"
                        + textSegment.metadata().getString("file_name")
                        + UUID.randomUUID();
            } catch (Exception exception){
                return defaultId;
            }

        }
        return defaultId;
    }

    private String getProjectId(Embedded embedded) {
        if (embedded instanceof TextSegment textSegment) {
            return Optional.ofNullable((textSegment).metadata().getString("project_id"))
                    .orElse("default");
        }
        return "default";
    }

    private Document createDocument(Embedding embedding, Embedded embedded, String filePath, String projectId) {
        Document doc = new Document();

        doc.add(new StringField("id", filePath, Field.Store.YES));

        if (embedded instanceof TextSegment segment) {
            doc.add(new StoredField("embedded", segment.text()));

            String lastIndexedDate = ZonedDateTime.now().toString();
            doc.add(new StringField("last_indexed_date", lastIndexedDate, Field.Store.YES));

            String metadata = serializeMetadata(segment.metadata());
            doc.add(new StoredField("metadata", metadata));
        }

        float[] vector = embedding.vector();
        FieldType vectorType = KnnFloatVectorField.createFieldType(vector.length, VectorSimilarityFunction.COSINE);
        doc.add(new KnnFloatVectorField("vector", vector, vectorType));

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
