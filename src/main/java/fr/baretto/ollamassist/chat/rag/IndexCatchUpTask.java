package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Brings an existing KnowledgeIndex up to date with the Workspace: indexes new and modified
 * files, removes deleted ones, and leaves everything else as it is. The decision is made by
 * {@link IndexCatchUp}; this task only collects the inputs and applies the result.
 */
@Slf4j
public class IndexCatchUpTask extends Task.Backgroundable {

    private final IndexRegistry registry;

    public IndexCatchUpTask(@NotNull Project project, IndexRegistry registry) {
        super(project, "OllamAssist - Knowledge Index Catch-Up", true);
        this.registry = registry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run(@NotNull ProgressIndicator indicator) {
        String projectId = getProject().getName();
        if (registry.indexationIsProcessing(projectId)) {
            return;
        }
        registry.markAsCurrentIndexation(projectId);
        try {
            LuceneEmbeddingStore<TextSegment> store = getProject().getService(LuceneEmbeddingStore.class);
            DocumentIndexingPipeline pipeline = getProject().getService(DocumentIndexingPipeline.class);

            indicator.setText("Comparing the index with the workspace...");
            List<String> files = getProject().getService(FilesUtil.class).collectFilePaths();
            IndexCatchUp catchUp = IndexCatchUp.plan(store.indexedFiles(), lastModified(files),
                    file -> Files.exists(Path.of(file)));
            if (catchUp.isEmpty()) {
                return;
            }

            log.info("Index catch-up: {} file(s) to remove, {} to index",
                    catchUp.toRemove().size(), catchUp.toIndex().size());
            catchUp.toRemove().forEach(file -> store.removeAll(new IdStartWithFilter(file)));
            indicator.setText("Indexing changed files...");
            pipeline.addAllDocuments(catchUp.toIndex());
            pipeline.flush(indicator::isCanceled, indexed -> { });
        } catch (Exception e) {
            log.warn("Index catch-up failed; the index is left as it was", e);
        } finally {
            registry.removeFromCurrentIndexation(projectId);
        }
    }

    private static Map<String, Instant> lastModified(List<String> files) {
        Map<String, Instant> modified = new HashMap<>();
        for (String file : files) {
            try {
                modified.put(file, Files.getLastModifiedTime(Path.of(file)).toInstant());
            } catch (IOException e) {
                log.debug("Skipping {} during catch-up: {}", file, e.getMessage());
            }
        }
        return modified;
    }
}
