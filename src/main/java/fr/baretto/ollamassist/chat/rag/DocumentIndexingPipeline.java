package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.project.Project;
import com.intellij.util.Producer;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntConsumer;

@Slf4j
public class DocumentIndexingPipeline implements AutoCloseable {

    private static final int BATCH_SIZE = 10;
    private static final String PROCESSING_ERROR_FORMAT = "Failed to process document: %s";
    private static final String REQUEUE_LOG_FORMAT = "Re-queueing document (attempt %d/%d): %s";
    private static final String PERMANENT_FAILURE_FORMAT = "Permanent failure after %d attempts: %s";
    private static final String INDEXED_COUNT_FORMAT = "Successfully indexed %d documents";
    private static final int SYNCHRONOUS_BATCH_SIZE = 100;
    private static final int LOG_INTERVAL = 100;
    private static final int MAX_RETRIES = 3;

    private final LuceneEmbeddingStore<TextSegment> embeddingStore;
    private final Project project;
    private final Map<String, AtomicInteger> fileRetries = new ConcurrentHashMap<>();
    private final LinkedBlockingDeque<String> processingQueue = new LinkedBlockingDeque<>();
    private final Set<String> pendingDocumentIds = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ReentrantLock processingLock = new ReentrantLock(true);
    private final Phaser processingPhaser = new Phaser(1);
    private final AtomicInteger totalIndexedDocuments = new AtomicInteger(0);
    private EmbeddingStoreIngestor ingestor;
    private volatile boolean isRunning = false;

    public DocumentIndexingPipeline(Project project) {
        this.embeddingStore = project.getService(LuceneEmbeddingStore.class);
        this.ingestor = DocumentIngestFactory.create(embeddingStore);
        this.project = project;
        start();
    }

    public synchronized void flush(Producer<Boolean> shouldContinue, IntConsumer consumer) {
        processingPhaser.register();
        try {
            processingLock.lockInterruptibly();
            try {
                log.info("Starting synchronous flush ({} documents queued)", processingQueue.size());

                List<String> currentBatch = new ArrayList<>();
                while (!processingQueue.isEmpty()) {
                    if (Boolean.TRUE.equals(shouldContinue.get())) {
                        break;
                    }
                    processingQueue.drainTo(currentBatch, SYNCHRONOUS_BATCH_SIZE);
                    processDocuments(currentBatch);
                    consumer.accept(currentBatch.size());
                    currentBatch.clear();
                }
            } finally {
                processingLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            processingPhaser.arriveAndDeregister();
            log.info("Synchronous flush completed");
        }
    }

    public boolean addDocument(String filePath) {
        if (pendingDocumentIds.add(filePath)) {
            return processingQueue.offer(filePath);
        }
        return false;
    }

    public int addAllDocuments(Collection<String> filePaths) {
        int added = 0;
        for (String path : filePaths) {
            if (addDocument(path)) {
                added++;
            }
        }
        return added;
    }

    private void start() {
        isRunning = true;
        scheduler.scheduleWithFixedDelay(this::processBatch, 0, 30, TimeUnit.SECONDS);
    }

    private void processBatch() {
        if (!isRunning || processingQueue.isEmpty()) return;

        processingPhaser.arriveAndAwaitAdvance();
        processingLock.lock();
        try {
            List<String> currentBatch = new ArrayList<>(BATCH_SIZE);
            processingQueue.drainTo(currentBatch, BATCH_SIZE);

            if (!currentBatch.isEmpty()) {
                log.debug("Processing async batch of {} documents", currentBatch.size());
                processDocuments(currentBatch);
                logProgress();
            }
        } catch (Exception e) {
            log.error("Batch processing error", e);
        } finally {
            processingLock.unlock();
        }
    }

    public void processDocuments(List<String> filePaths) {
        for (String filePath : filePaths) {
            try {
                processSingleDocument(filePath);
            } catch (Exception e) {
                handleDocumentError(filePath, e);
            }
        }
    }

    private void processSingleDocument(String filePath) {
        try {
            Document doc = FileSystemDocumentLoader.loadDocument(Path.of(filePath));
            ingestor.ingest(doc);
            pendingDocumentIds.remove(filePath);
            totalIndexedDocuments.incrementAndGet();
            fileRetries.remove(filePath);
        } catch (Exception e) {
            throw new ProcessingException(String.format(PROCESSING_ERROR_FORMAT, filePath), e);
        }
    }

    public void processSingleDocument(Document document) {
        try {
            ingestor.ingest(document);
            totalIndexedDocuments.incrementAndGet();
        } catch (Exception e) {
            handleCorruption();
        }
    }

    private void handleDocumentError(String filePath, Exception e) {
        int retryCount = fileRetries.computeIfAbsent(filePath, k -> new AtomicInteger(0)).incrementAndGet();

        if (retryCount <= MAX_RETRIES) {
            log.warn(String.format(REQUEUE_LOG_FORMAT, retryCount, MAX_RETRIES, filePath));
            reQueueDocument(filePath);
        } else {
            log.error(String.format(PERMANENT_FAILURE_FORMAT, MAX_RETRIES, filePath));
            pendingDocumentIds.remove(filePath);
            fileRetries.remove(filePath);
        }

        if (e instanceof CorruptedIndexException) {
            handleCorruption();
        }
    }

    private void reQueueDocument(String filePath) {
        processingQueue.addFirst(filePath);
        pendingDocumentIds.add(filePath);
    }

    private void logProgress() {
        int count = totalIndexedDocuments.get();
        if (count > 0 && count % LOG_INTERVAL == 0) {
            log.info(String.format(INDEXED_COUNT_FORMAT, count));
        }
    }

    public void handleCorruption() {
        processingLock.lock();
        try {
            log.warn("Index corruption detected - Recreating index...");
            embeddingStore.recreateIndex();
            ingestor = DocumentIngestFactory.create(embeddingStore);

            log.info("Index recreated - Resuming operations");
        } catch (Exception ex) {
            log.error("Critical error during index recovery", ex);
            close();
        } finally {
            processingLock.unlock();
        }
    }

    @Override
    public void close() {
        isRunning = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class ProcessingException extends RuntimeException {
        ProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}