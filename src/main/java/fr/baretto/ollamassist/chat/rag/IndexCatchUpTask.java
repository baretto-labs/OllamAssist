package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Brings an up-to-date KnowledgeIndex in line with the Workspace after RAG was switched back on:
 * new and modified files are indexed, deleted ones removed, the rest left as it is.
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
            IndexCatchUp catchUp = new IndexSynchronizer(store, pipeline::addAllDocuments).synchronize(files);
            if (catchUp.isEmpty()) {
                return;
            }

            log.info("Index catch-up: {} file(s) removed, {} to index",
                    catchUp.toRemove().size(), catchUp.toIndex().size());
            indicator.setText("Indexing changed files...");
            pipeline.flush(indicator::isCanceled, indexed -> { });
        } catch (Exception e) {
            log.warn("Index catch-up failed; the index is left as it was", e);
        } finally {
            registry.removeFromCurrentIndexation(projectId);
        }
    }
}
