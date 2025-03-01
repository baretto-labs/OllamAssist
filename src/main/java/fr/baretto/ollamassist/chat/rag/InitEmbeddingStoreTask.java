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
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
public class InitEmbeddingStoreTask extends Task.Backgroundable {

    private final EmbeddingStore<TextSegment> store;
    private final AtomicInteger processedFiles = new AtomicInteger(0);
    private long totalFiles;

    public InitEmbeddingStoreTask(@Nullable Project project, EmbeddingStore<TextSegment> store) {
        super(project, "OllamAssist - Knowledge Indexing", true);
        this.store = store;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            indicator.setText("Collecting files...");
            FilesUtil filesUtil = getProject().getService(FilesUtil.class);
            List<String> filePaths = filesUtil.collectFilePaths();
            totalFiles = filePaths.size();

            indicator.setText2("Indexing files...");
            processBatchesSequentially(filePaths, indicator);
            if (!indicator.isCanceled()) {
                new IndexRegistry().markAsIndexed(getProject().getName());
            }

        } catch (Exception e) {
            handleError(e, indicator);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
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
            EmbeddingStoreIngestor.ingest(documents, store);
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