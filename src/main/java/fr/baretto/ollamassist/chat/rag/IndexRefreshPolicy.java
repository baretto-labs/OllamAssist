package fr.baretto.ollamassist.chat.rag;

/**
 * Decides, for each event that affects the KnowledgeIndex, which indexes must be rebuilt and
 * what the user is told. Only an embedding model change or a real corruption discards an index:
 * switching RAG on keeps what is already indexed and only catches up with the files changed
 * meanwhile.
 */
public final class IndexRefreshPolicy {

    static final String EMBEDDING_MODEL_CHANGED =
            "Embedding model changed: the index is being rebuilt with the new model.";
    static final String INDEX_CORRUPTED =
            "The index of this project was corrupted and is being rebuilt.";

    private final String projectId;
    private final IndexRegistry registry;
    private final IndexStatusReporter reporter;
    private final Runnable indexing;
    private final Runnable catchUp;

    /**
     * @param indexing indexes the project when its index is missing, stale or corrupted
     * @param catchUp  applies to an existing index the changes made in the Workspace meanwhile
     */
    public IndexRefreshPolicy(String projectId, IndexRegistry registry, IndexStatusReporter reporter,
                              Runnable indexing, Runnable catchUp) {
        this.projectId = projectId;
        this.registry = registry;
        this.reporter = reporter;
        this.indexing = indexing;
        this.catchUp = catchUp;
    }

    /**
     * While RAG was off, file changes were not followed, so an existing index may be behind the
     * Workspace. It is brought up to date file by file instead of being rebuilt.
     */
    public void ragSwitchedOn() {
        if (registry.isIndexed(projectId)) {
            catchUp.run();
        } else {
            indexing.run();
        }
    }

    /**
     * The user asked for a clean index: this project's index is discarded and rebuilt. Nothing
     * is reported, the user just asked for it.
     */
    public void clearRequested() {
        registry.markAsCorrupted(projectId);
        indexing.run();
    }

    /**
     * Vectors computed by another model are not comparable with the new ones, so every
     * project's index is invalid, not only this one.
     */
    public void embeddingModelChanged() {
        registry.markAllAsCorrupted();
        reporter.info(EMBEDDING_MODEL_CHANGED);
        indexing.run();
    }

    public void indexCorrupted() {
        registry.markAsCorrupted(projectId);
        reporter.warning(INDEX_CORRUPTED);
        indexing.run();
    }
}
