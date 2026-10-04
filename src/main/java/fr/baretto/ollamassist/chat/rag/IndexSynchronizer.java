package fr.baretto.ollamassist.chat.rag;

import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Brings the KnowledgeIndex in line with a list of Workspace files, as decided by
 * {@link IndexCatchUp}. On an empty index every file is queued, so this is also how a full
 * indexing runs: a file already indexed and unchanged is never added a second time.
 */
@Slf4j
public final class IndexSynchronizer {

    private final LuceneEmbeddingStore<TextSegment> store;
    private final Consumer<Collection<String>> indexing;

    /**
     * @param indexing receives the files to (re)index; their old segments are already removed
     */
    public IndexSynchronizer(LuceneEmbeddingStore<TextSegment> store, Consumer<Collection<String>> indexing) {
        this.store = store;
        this.indexing = indexing;
    }

    public IndexCatchUp synchronize(List<String> files) {
        IndexCatchUp catchUp = IndexCatchUp.plan(store.indexedFiles(), lastModified(files),
                file -> Files.exists(Path.of(file)));
        catchUp.toRemove().forEach(file -> store.removeAll(new IdStartWithFilter(file)));
        if (!catchUp.toIndex().isEmpty()) {
            indexing.accept(catchUp.toIndex());
        }
        return catchUp;
    }

    private static Map<String, Instant> lastModified(List<String> files) {
        Map<String, Instant> modified = new HashMap<>();
        for (String file : files) {
            try {
                modified.put(file, Files.getLastModifiedTime(Path.of(file)).toInstant());
            } catch (IOException e) {
                log.debug("Skipping {}: {}", file, e.getMessage());
            }
        }
        return modified;
    }
}
