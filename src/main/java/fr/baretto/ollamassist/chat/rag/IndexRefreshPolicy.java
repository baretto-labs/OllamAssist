package fr.baretto.ollamassist.chat.rag;

/**
 * Decides, for each event that affects the KnowledgeIndex, which indexes must be rebuilt and
 * what the user is told. Only an embedding model change or a real corruption discards an index:
 * switching RAG on keeps what is already indexed.
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

    public IndexRefreshPolicy(String projectId, IndexRegistry registry, IndexStatusReporter reporter, Runnable indexing) {
        this.projectId = projectId;
        this.registry = registry;
        this.reporter = reporter;
        this.indexing = indexing;
    }

    public void ragSwitchedOn() {
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
