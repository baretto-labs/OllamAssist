package fr.baretto.ollamassist.benchmark;

import java.util.List;

/**
 * Question corpus for the chunking benchmark.
 * Based on the OllamAssist codebase — same 30 questions as the external benchmark.
 *
 * <p>Three difficulty tiers:
 * <ul>
 *   <li>{@link Difficulty#LOCAL} — single class or method</li>
 *   <li>{@link Difficulty#STRUCTURAL} — inheritance and direct relationships</li>
 *   <li>{@link Difficulty#CROSS_MODULE} — multi-package traversal</li>
 * </ul>
 *
 * <p>{@code expectedHints} are FQN fragments (case-insensitive) used for deterministic
 * {@code hintCoverage} scoring, independent of the LLM judge.
 */
public class Questions {

    public enum Difficulty { LOCAL, STRUCTURAL, CROSS_MODULE }

    public record Question(String text, Difficulty difficulty, String[] expectedHints) {}

    public static final List<Question> ALL = List.of(

        // ── LOCAL (10) ───────────────────────────────────────────────────────

        new Question(
            "What does the retrieve method do in ContextRetriever?",
            Difficulty.LOCAL,
            new String[]{"ContextRetriever", "retrieve", "Content"}
        ),
        new Question(
            "What does the calculateDynamicThreshold method compute in LuceneEmbeddingStore?",
            Difficulty.LOCAL,
            new String[]{"LuceneEmbeddingStore", "calculateDynamicThreshold", "scores"}
        ),
        new Question(
            "How does DocumentIndexingPipeline handle document errors and retries?",
            Difficulty.LOCAL,
            new String[]{"DocumentIndexingPipeline", "handleDocumentError", "MAX_RETRIES"}
        ),
        new Question(
            "What authentication header does AuthenticationHelper generate?",
            Difficulty.LOCAL,
            new String[]{"AuthenticationHelper", "createBasicAuthHeader", "Base64"}
        ),
        new Question(
            "How does SuggestionCache generate cache keys for completion requests?",
            Difficulty.LOCAL,
            new String[]{"SuggestionCache", "generateCacheKey"}
        ),
        new Question(
            "What constants does DocumentIndexingPipeline define for batch processing?",
            Difficulty.LOCAL,
            new String[]{"DocumentIndexingPipeline", "BATCH_SIZE", "MAX_RETRIES", "SYNCHRONOUS_BATCH_SIZE"}
        ),
        new Question(
            "How does EnhancedCompletionService clean up raw LLM suggestions?",
            Difficulty.LOCAL,
            new String[]{"EnhancedCompletionService", "processSuggestion", "removeCodeBlockMarkers"}
        ),
        new Question(
            "What does the dismiss method do in RefactorAction?",
            Difficulty.LOCAL,
            new String[]{"RefactorAction", "dismiss"}
        ),
        new Question(
            "How does PrerequisiteService check if the local embedding model is available?",
            Difficulty.LOCAL,
            new String[]{"PrerequisiteService", "checkLocalEmbeddingModel"}
        ),
        new Question(
            "What does the fuse method compute in RRFFusion?",
            Difficulty.LOCAL,
            new String[]{"RRFFusion", "fuse", "RRF_K"}
        ),

        // ── STRUCTURAL (10) ──────────────────────────────────────────────────

        new Question(
            "What interfaces does OllamaService implement?",
            Difficulty.STRUCTURAL,
            new String[]{"OllamaService", "Disposable", "ModelListener"}
        ),
        new Question(
            "What does LuceneEmbeddingStore implement?",
            Difficulty.STRUCTURAL,
            new String[]{"LuceneEmbeddingStore", "EmbeddingStore", "Closeable", "Disposable"}
        ),
        new Question(
            "What methods does the Assistant interface define?",
            Difficulty.STRUCTURAL,
            new String[]{"Assistant", "chat", "TokenStream"}
        ),
        new Question(
            "What does DocumentIndexingPipeline implement?",
            Difficulty.STRUCTURAL,
            new String[]{"DocumentIndexingPipeline", "AutoCloseable"}
        ),
        new Question(
            "What does OllamAssistStartup implement and what is its role?",
            Difficulty.STRUCTURAL,
            new String[]{"OllamAssistStartup", "ProjectActivity", "execute"}
        ),
        new Question(
            "What class extends AnAction for the refactoring feature?",
            Difficulty.STRUCTURAL,
            new String[]{"RefactorAction", "AnAction"}
        ),
        new Question(
            "What interface does ContextRetriever implement and what does it override?",
            Difficulty.STRUCTURAL,
            new String[]{"ContextRetriever", "ContentRetriever", "retrieve"}
        ),
        new Question(
            "What interface does HybridRetriever implement?",
            Difficulty.STRUCTURAL,
            new String[]{"HybridRetriever", "ContentRetriever"}
        ),
        new Question(
            "Which settings classes implement PersistentStateComponent?",
            Difficulty.STRUCTURAL,
            new String[]{"PersistentStateComponent"}
        ),
        new Question(
            "What does ConversationService implement?",
            Difficulty.STRUCTURAL,
            new String[]{"ConversationService"}
        ),

        // ── CROSS_MODULE (10) ────────────────────────────────────────────────

        new Question(
            "How does OllamAssistStartup initialize EditorListener on project load?",
            Difficulty.CROSS_MODULE,
            new String[]{"OllamAssistStartup", "EditorListener", "execute"}
        ),
        new Question(
            "How does DocumentIndexingPipeline interact with LuceneEmbeddingStore to persist documents?",
            Difficulty.CROSS_MODULE,
            new String[]{"DocumentIndexingPipeline", "LuceneEmbeddingStore"}
        ),
        new Question(
            "How does ContextRetriever combine results from WorkspaceContextRetriever and DuckDuckGoContentRetriever?",
            Difficulty.CROSS_MODULE,
            new String[]{"ContextRetriever", "WorkspaceContextRetriever", "DuckDuckGoContentRetriever"}
        ),
        new Question(
            "How does OllamaService initialize LuceneEmbeddingStore and DocumentIndexingPipeline together?",
            Difficulty.CROSS_MODULE,
            new String[]{"OllamaService", "LuceneEmbeddingStore", "DocumentIndexingPipeline"}
        ),
        new Question(
            "How does HybridRetriever combine KNN and BM25 results via RRF?",
            Difficulty.CROSS_MODULE,
            new String[]{"HybridRetriever", "RRFFusion", "knnSearch", "bm25Search"}
        ),
        new Question(
            "How does DocumentIngestFactory create the embedding model with DJL fallback to Ollama?",
            Difficulty.CROSS_MODULE,
            new String[]{"DocumentIngestFactory", "createEmbeddingModel", "BgeSmallEnV15Quantized", "OllamaEmbeddingModel"}
        ),
        new Question(
            "How does ConversationService persist conversations via ConversationRepository?",
            Difficulty.CROSS_MODULE,
            new String[]{"ConversationService", "ConversationRepository", "save"}
        ),
        new Question(
            "How does OllamaService restore chat memory when a conversation is switched?",
            Difficulty.CROSS_MODULE,
            new String[]{"OllamaService", "restoreMemory", "ConversationSwitchedNotifier"}
        ),
        new Question(
            "How does PrerequisiteService fall back from local DJL embedding to Ollama?",
            Difficulty.CROSS_MODULE,
            new String[]{"PrerequisiteService", "checkEmbeddingModelAsync"}
        ),
        new Question(
            "How does LuceneEmbeddingStore detect and migrate incompatible index versions?",
            Difficulty.CROSS_MODULE,
            new String[]{"LuceneEmbeddingStore", "checkAndMigrateIndexVersion", "INDEX_VERSION"}
        )
    );

    private Questions() {}
}
