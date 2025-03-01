package fr.baretto.ollamassist.chat.rag;

import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
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


    private static final int PROGRESS_UPDATE_THRESHOLD = 50;
    private final EmbeddingStore<TextSegment> store;
    private final AtomicInteger processedFiles = new AtomicInteger(0);
    private final ProjectFileIndex fileIndex;
    private int processedBatches;
    private long totalFiles;

    private long startTime;

    public InitEmbeddingStoreTask(@Nullable Project project, EmbeddingStore<TextSegment> store) {
        super(project, "OllamAssist - Knowledge Indexing", true);
        this.store = store;
        this.fileIndex = ProjectFileIndex.getInstance(project);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
        startTime = System.currentTimeMillis();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            long totalStartTime = System.currentTimeMillis();
                indicator.setText("Collecting files...");
                FilesUtil filesUtil = getProject().getService(FilesUtil.class);
                List<String> filePaths = filesUtil.collectFilePaths();
                totalFiles = filePaths.size();

                indicator.setText2("Indexing files...");
                processBatchesSequentially(filePaths, indicator);


            if (!indicator.isCanceled()) {
                new IndexRegistry().markAsIndexed(getProject().getName());
            }
            long totalEndTime = System.currentTimeMillis();
            long totalDuration = totalEndTime - totalStartTime;

            log.error("Temps total d'indexation : {}", formatDuration(totalDuration));
            log.error("Moyenne par lot : {} ms/lot", totalDuration / processedBatches);
            double filesPerSec = (double) 100 / (totalDuration / 1000.0);
            String speedText = String.format("Vitesse: %.1f fichiers/sec", filesPerSec);
            long usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
            String memoryText = String.format("Mémoire: %d MB", usedMemory);
            log.error(memoryText);

        } catch (Exception e) {
            handleError(e, indicator);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
    private void processBatchesSequentially(List<String> filePaths, ProgressIndicator indicator) {
        List<List<String>> batches = Lists.partition(filePaths, 100);
        processedBatches = batches.size();

        for (List<String> batch : batches) {
            if (indicator.isCanceled()) break;

            List<Document> documents = new ArrayList<>(100);
            for (String path : batch) {
                processSingleFile(path, documents, indicator);
            }

            ingestDocuments(documents, indicator);
            cleanupBatchResources(documents);
            updateProgress(indicator, batch.size());
        }
    }

    private void processSingleFile(String path, List<Document> documents, ProgressIndicator indicator) {
        try {
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
            if (file != null && file.isValid()) {
                Document doc = FileSystemDocumentLoader.loadDocument(Path.of(file.getPath()));
                documents.add(doc);
            }
        } catch (Exception e) {
            log.error("Error processing {}: {}", path, e.getMessage());
        } finally {
            indicator.checkCanceled();
        }
    }

    private void ingestDocuments(List<Document> documents, ProgressIndicator indicator) {
        if (!documents.isEmpty()) {
            EmbeddingStoreIngestor.ingest(documents, store);
        }
    }

    private void cleanupBatchResources(List<Document> documents) {
        documents.clear();
        documents = null;
    }

    private void updateProgress(ProgressIndicator indicator, int batchSize) {
        processedFiles.addAndGet(batchSize);
        ApplicationManager.getApplication().invokeLater(() -> {
            double progress = (double) processedFiles.get() / totalFiles;
            indicator.setFraction(progress);
            indicator.setText2(processedFiles + "/" + totalFiles + " files");
        });
    }

    private void updateProgress(ProgressIndicator indicator, double progress, int batchSize, int totalProcessed) {
        indicator.setFraction(progress);

        if (totalProcessed % PROGRESS_UPDATE_THRESHOLD == 0 || totalProcessed == totalFiles) {
            String progressText = String.format(
                    "Indexing: %d/%d files (%.1f%%) - Last batch: %d files",
                    totalProcessed,
                    totalFiles,
                    progress * 100,
                    batchSize
            );
            indicator.setText2(progressText);
            if (totalProcessed > 0 && totalFiles > 0) {
                estimateRemainingTime(indicator, totalProcessed);
            }
        } else {
            String progressText = String.format(
                    "Indexing: %d/%d files (%.1f%%)",
                    totalProcessed,
                    totalFiles,
                    progress * 100
            );
            indicator.setText2(progressText);
        }
    }

    private void estimateRemainingTime(ProgressIndicator indicator, int processed) {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = (long) ((elapsed / (double) processed) * (totalFiles - processed));

        String timeEstimate = formatDuration(remaining);
        indicator.setText2(indicator.getText2() + " - Estimated remaining time: " + timeEstimate);
    }

    private String formatDuration(long millis) {
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void handleError(Exception e, ProgressIndicator indicator) {
        log.error("Indexing failed", e);
        indicator.setText2("Failed - " + e.getMessage());
    }

}