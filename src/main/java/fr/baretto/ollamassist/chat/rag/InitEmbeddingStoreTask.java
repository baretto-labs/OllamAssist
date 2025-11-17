package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
public class InitEmbeddingStoreTask extends Task.Backgroundable {

    private static final String FILES_PROGRESS_FORMAT = "%d/%d files";
    private static final String ERROR_PREFIX = "Failed - ";

    private final AtomicInteger processedFiles = new AtomicInteger(0);
    private final IndexRegistry indexationRegistry;
    private final DocumentIndexingPipeline documentIndexingPipeline;
    private long totalFiles;

    public InitEmbeddingStoreTask(@Nullable Project project, IndexRegistry indexationRegistry) {
        super(project, "OllamAssist - Knowledge Indexing", true);
        this.indexationRegistry = indexationRegistry;
        this.documentIndexingPipeline = getProject().getService(DocumentIndexingPipeline.class);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
        if (!indexationRegistry.isIndexed(getProject().getName()) || indexationRegistry.isCorrupted(getProject().getName())) {

            if (indexationRegistry.isCorrupted(getProject().getName())) {
                documentIndexingPipeline.handleCorruption();
                indexationRegistry.markAsCleared(getProject().getName());
            }

            indexationRegistry.markAsCurrentIndexation(getProject().getName());
            ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                indicator.setText("Collecting files...");
                FilesUtil filesUtil = getProject().getService(FilesUtil.class);
                List<String> filePaths = filesUtil.collectFilePaths();
                totalFiles = filePaths.size();
                if (totalFiles > filesUtil.getMaxFiles()) {
                    totalFiles = filesUtil.getMaxFiles();
                    filePaths = filePaths.subList(0, filesUtil.getMaxFiles());
                    indicator.setText2("Indexing files...");
                } else {
                    indicator.setText2("Indexing files...");
                }
                documentIndexingPipeline.addAllDocuments(filePaths);
                documentIndexingPipeline.flush(indicator::isCanceled, indexedFiles -> updateProgress(indicator, indexedFiles));

                if (!indicator.isCanceled()) {
                    new IndexRegistry().markAsIndexed(getProject().getName());
                }

            } catch (Exception e) {
                handleError(e, indicator);
            } finally {
                indexationRegistry.removeFromCurrentIndexation(getProject().getName());
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        }

    }

    private void updateProgress(ProgressIndicator indicator, int batchSize) {
        processedFiles.addAndGet(batchSize);
        ApplicationManager.getApplication().invokeLater(() -> {
            double progress = (double) processedFiles.get() / totalFiles;
            indicator.setFraction(progress);
            indicator.setText2(String.format(FILES_PROGRESS_FORMAT, processedFiles.get(), totalFiles));
        });
    }

    private void handleError(Exception e, ProgressIndicator indicator) {
        log.error("Indexing failed", e);
        indicator.setText2(ERROR_PREFIX + e.getMessage());
    }

}