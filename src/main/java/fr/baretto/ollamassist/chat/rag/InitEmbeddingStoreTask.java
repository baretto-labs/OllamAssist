package fr.baretto.ollamassist.chat.rag;

import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
public class InitEmbeddingStoreTask extends Task.Backgroundable {

    private final AtomicInteger processedFiles = new AtomicInteger(0);
    private final EmbeddingStore<TextSegment> store;
    private final IndexRegistry indexationRegistry;
    private long totalFiles;

    public InitEmbeddingStoreTask(@Nullable Project project, EmbeddingStore<TextSegment> store, IndexRegistry indexationRegistry) {
        super(project, "OllamAssist - Knowledge Indexing", true);
        this.store = store;
        this.indexationRegistry = indexationRegistry;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
        if (!indexationRegistry.isIndexed(getProject().getName())) {
            indexationRegistry.markAsCurrentIndexation(getProject().getName());
            ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                indicator.setText("Collecting files...");
                FilesUtil filesUtil = getProject().getService(FilesUtil.class);
                List<String> filePaths = filesUtil.collectFilePaths();
                totalFiles = filePaths.size();
                if (totalFiles > FilesUtil.getMaxFiles()) {
                    totalFiles = FilesUtil.getMaxFiles();
                    filePaths  = filePaths.subList(0, FilesUtil.getMaxFiles());
                    indicator.setText2("Indexing files...");
                } else {
                    indicator.setText2("Indexing files...");
                }

                processBatchesSequentially(filePaths, indicator);
                if (!indicator.isCanceled()) {
                    new IndexRegistry().markAsIndexed(getProject().getName());
                }

            } catch (Exception e) {
                handleError(e, indicator);
            } finally {
                indexationRegistry.markAsIndexed(getProject().getName());
                indexationRegistry.removeFromCurrentIndexation(getProject().getName());
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        }

    }

    private void processBatchesSequentially(List<String> filePaths, ProgressIndicator indicator) {
        List<List<String>> batches = Lists.partition(filePaths, 100);

        for (List<String> batch : batches) {
            if (indicator.isCanceled()) break;

            List<Document> documents = new ArrayList<>(100);
            for (String path : batch) {
                try {
                    Document doc = FileSystemDocumentLoader.loadDocument(Path.of(path));
                    documents.add(doc);
                } catch (Exception e) {
                    log.error("Error processing {}: {}", path, e.getMessage());
                } finally {
                    indicator.checkCanceled();
                }
            }
            ingestDocuments(documents);
            documents.clear();
            updateProgress(indicator, batch.size());
        }
    }

    private void ingestDocuments(List<Document> documents) {
        if (!documents.isEmpty()) {
            DocumentIngestorFactory.create(store).ingest(documents);
        }
    }

    private void updateProgress(ProgressIndicator indicator, int batchSize) {
        processedFiles.addAndGet(batchSize);
        ApplicationManager.getApplication().invokeLater(() -> {
            double progress = (double) processedFiles.get() / totalFiles;
            indicator.setFraction(progress);
            indicator.setText2(processedFiles + "/" + totalFiles + " files");
        });
    }

    private void handleError(Exception e, ProgressIndicator indicator) {
        log.error("Indexing failed", e);
        indicator.setText2("Failed - " + e.getMessage());
    }

}