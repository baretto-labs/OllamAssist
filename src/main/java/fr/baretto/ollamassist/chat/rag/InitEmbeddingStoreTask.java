package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
public class InitEmbeddingStoreTask extends Task.Backgroundable {


    private static final int PROGRESS_UPDATE_THRESHOLD = 50;
    private final EmbeddingStore<TextSegment> store;
    private final AtomicInteger processedFiles = new AtomicInteger(0);
    private long totalFiles;
    private PathMatcher pathMatcher;
    private long startTime;

    public InitEmbeddingStoreTask(@Nullable Project project, EmbeddingStore<TextSegment> store) {
        super(project, "OllamAssist - Knowledge Indexing", true);
        this.store = store;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
        startTime = System.currentTimeMillis();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            indicator.setIndeterminate(false);
            indicator.setText("Preparing indexing...");


            pathMatcher = new ShouldBeIndexed();

            countTotalFiles(indicator);
            if (totalFiles == 0) {
                indicator.setText2("No files to index");
                return;
            }
            processFiles(indicator, pathMatcher);
            if (!indicator.isCanceled()) {
                new IndexRegistry().markAsIndexed(getProject().getName());
            }
        } catch (Exception e) {
            handleError(e, indicator);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private void countTotalFiles(ProgressIndicator indicator) throws Exception {
        indicator.setText("Analyzing project structure...");
        try (Stream<Path> stream = Files.walk(Path.of(getProject().getBasePath()))) {
            totalFiles = stream
                    .filter(path -> pathMatcher.matches(path))
                    .count();
        }
        indicator.setText2(String.format("Files to index: %d", totalFiles));
    }

    private void processFiles(ProgressIndicator indicator, PathMatcher pathMatcher) {
        FilesUtil.batch(
                getProject().getName(),
                getProject().getBasePath(),
                pathMatcher,
                docs -> handleBatch(docs, indicator)
        );
    }

    private void handleBatch(List<Document> batch, ProgressIndicator indicator) {
        if (indicator.isCanceled()) return;

        int currentCount = processedFiles.addAndGet(batch.size());
        double progress = (double) currentCount / totalFiles;

        updateProgress(indicator, progress, batch.size(), currentCount);

        try {
            EmbeddingStoreIngestor.ingest(batch, store);
        } catch (Exception e) {
            log.error("Error during indexing of {} files", batch.size(), e);
            indicator.setText2("Error in a batch of files - Check logs");
        }
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