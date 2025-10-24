package fr.baretto.ollamassist.completion;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import fr.baretto.ollamassist.auth.AuthenticationHelper;
import fr.baretto.ollamassist.chat.rag.LuceneEmbeddingStore;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Enhanced context provider for intelligent code completion.
 * Integrates with OllamAssist's existing RAG infrastructure to provide
 * rich, contextual information for AI-powered code suggestions.
 */
@Slf4j
public class EnhancedContextProvider {
    
    private static final int IMMEDIATE_CONTEXT_WINDOW = 1500;
    private static final int SEARCH_RESULTS_LIMIT = 3;
    private static final double MIN_SIMILARITY_SCORE = 0.6;
    private static final int TIMEOUT_SECONDS = 2;
    
    private final Project project;
    private final LuceneEmbeddingStore<?> embeddingStore;
    private EmbeddingModel embeddingModel;
    
    public EnhancedContextProvider(@NotNull Project project) {
        this.project = project;
        this.embeddingStore = project.getService(LuceneEmbeddingStore.class);
        initializeEmbeddingModel();
    }
    
    /**
     * Builds comprehensive completion context by analyzing the current editor state
     * and retrieving relevant information from the project's indexed codebase.
     */
    @NotNull
    public CompletableFuture<CompletionContext> buildCompletionContextAsync(@NotNull Editor editor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ReadAction.compute(() -> buildCompletionContext(editor));
            } catch (Exception e) {
                log.warn("Failed to build enhanced completion context, falling back to basic context", e);
                return buildBasicContext(editor);
            }
        }).orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
          .exceptionally(throwable -> {
              log.warn("Context building timed out, using basic context", throwable);
              return buildBasicContext(editor);
          });
    }
    
    /**
     * Synchronous version for immediate context building.
     */
    @NotNull
    public CompletionContext buildCompletionContext(@NotNull Editor editor) {
        try {
            String immediateContext = getImmediateContext(editor);
            String projectContext = getProjectContext(editor);
            String similarPatterns = getSimilarCodePatterns(immediateContext);
            String fileExtension = getFileExtension(editor);
            int cursorOffset = editor.getCaretModel().getOffset();
            CompletionContext.FileMetadata metadata = buildFileMetadata(editor);
            
            return CompletionContext.builder()
                .immediateContext(immediateContext)
                .projectContext(projectContext)
                .similarPatterns(similarPatterns)
                .fileExtension(fileExtension)
                .cursorOffset(cursorOffset)
                .fileMetadata(metadata)
                .build();
                
        } catch (Exception e) {
            log.warn("Error building completion context", e);
            return buildBasicContext(editor);
        }
    }
    
    /**
     * Extracts intelligent immediate context around the cursor position.
     * Uses a larger text window for better context understanding.
     */
    @NotNull
    private String getImmediateContext(@NotNull Editor editor) {
        Document document = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        
        // For now, use enhanced text window approach
        // TODO: Re-enable PSI-based context extraction once PSI dependencies are resolved
        return getEnhancedTextWindow(document, offset);
    }
    
    /**
     * Gets a text window around the cursor position as fallback.
     */
    @NotNull
    private String getTextWindow(@NotNull Document document, int offset) {
        int start = Math.max(0, offset - IMMEDIATE_CONTEXT_WINDOW / 2);
        int end = Math.min(document.getTextLength(), offset + IMMEDIATE_CONTEXT_WINDOW / 2);
        return document.getText(new TextRange(start, end));
    }
    
    /**
     * Gets an enhanced text window that includes more context and tries to include complete lines.
     */
    @NotNull
    private String getEnhancedTextWindow(@NotNull Document document, int offset) {
        // Get a larger window
        int start = Math.max(0, offset - IMMEDIATE_CONTEXT_WINDOW);
        int end = Math.min(document.getTextLength(), offset + IMMEDIATE_CONTEXT_WINDOW / 2);
        
        // Try to align to line boundaries for better context
        try {
            int startLine = document.getLineNumber(start);
            int endLine = document.getLineNumber(end);
            
            start = document.getLineStartOffset(startLine);
            if (endLine < document.getLineCount() - 1) {
                end = document.getLineEndOffset(endLine);
            }
        } catch (Exception e) {
            // Fallback to basic range if line operations fail
        }
        
        return document.getText(new TextRange(start, end));
    }
    
    /**
     * Extracts project-level context from the current file.
     * Simplified version that reads the file content directly.
     */
    @Nullable
    private String getProjectContext(@NotNull Editor editor) {
        try {
            VirtualFile virtualFile = FileEditorManager.getInstance(project).getSelectedFiles()[0];
            if (virtualFile == null) return null;
            
            // Read the beginning of the file to get imports and class structure
            Document document = editor.getDocument();
            String fileContent = document.getText();
            
            // Extract first 2000 characters which should include package, imports, and class declaration
            int contextLength = Math.min(2000, fileContent.length());
            return fileContent.substring(0, contextLength);
            
            // TODO: Re-enable PSI-based project context extraction once PSI dependencies are resolved
        } catch (Exception e) {
            log.debug("Failed to extract project context", e);
            return null;
        }
    }
    
    /**
     * Retrieves similar code patterns from the indexed codebase using semantic search.
     */
    @Nullable
    private String getSimilarCodePatterns(@NotNull String queryContext) {
        if (embeddingModel == null || embeddingStore == null) {
            log.debug("Embedding model or store not available for similar patterns retrieval");
            return null;
        }
        
        try {
            // Create embedding for the query context
            Embedding queryEmbedding = embeddingModel.embed(queryContext).content();
            
            // Search for similar patterns
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(SEARCH_RESULTS_LIMIT)
                .minScore(MIN_SIMILARITY_SCORE)
                .build();
                
            EmbeddingSearchResult<?> searchResult = embeddingStore.search(request);
            
            if (searchResult.matches().isEmpty()) {
                log.debug("No similar patterns found above similarity threshold");
                return null;
            }
            
            // Format similar patterns
            return searchResult.matches().stream()
                .map(match -> {
                    String text = ((TextSegment) match.embedded()).text();
                    // Limit individual pattern size
                    return text.length() > 500 ? text.substring(0, 500) + "..." : text;
                })
                .collect(Collectors.joining("\n--- Similar Pattern ---\n"));
                
        } catch (Exception e) {
            log.debug("Failed to retrieve similar code patterns", e);
            return null;
        }
    }
    
    /**
     * Builds basic file metadata for context enhancement.
     * Simplified version without advanced PSI analysis.
     */
    @Nullable
    private CompletionContext.FileMetadata buildFileMetadata(@NotNull Editor editor) {
        try {
            VirtualFile virtualFile = FileEditorManager.getInstance(project).getSelectedFiles()[0];
            if (virtualFile == null) return null;
            
            // TODO: Re-enable PSI-based metadata extraction once PSI dependencies are resolved
            return CompletionContext.FileMetadata.builder()
                .packageName(null) // Will be extracted from project context
                .className(null)   // Will be extracted from project context
                .currentMethodName(null)
                .currentMethodSignature(null)
                .insideMethod(false)
                .insideClass(false)
                .insideComment(false)
                .build();
                
        } catch (Exception e) {
            log.debug("Failed to build file metadata", e);
            return null;
        }
    }
    
    /**
     * Gets file extension from the current editor.
     */
    @NotNull
    private String getFileExtension(@NotNull Editor editor) {
        VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
        if (files.length > 0 && files[0].getExtension() != null) {
            return files[0].getExtension();
        }
        return "java"; // Default fallback
    }
    
    /**
     * Creates a basic context when enhanced context building fails.
     */
    @NotNull
    private CompletionContext buildBasicContext(@NotNull Editor editor) {
        Document document = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        
        int start = Math.max(0, offset - 200);
        int end = Math.min(document.getTextLength(), offset);
        String basicContext = document.getText(new TextRange(start, end));
        
        return CompletionContext.builder()
            .immediateContext(basicContext)
            .fileExtension(getFileExtension(editor))
            .cursorOffset(offset)
            .build();
    }
    
    /**
     * Initializes the embedding model for semantic search.
     */
    private void initializeEmbeddingModel() {
        try {
            OllamAssistSettings settings = OllamAssistSettings.getInstance();
            OllamaEmbeddingModel.OllamaEmbeddingModelBuilder builder = OllamaEmbeddingModel.builder()
                .baseUrl(settings.getEmbeddingOllamaUrl())
                .modelName(settings.getEmbeddingModelName())
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS));
            
            // Add authentication if configured
            if (AuthenticationHelper.isAuthenticationConfigured()) {
                Map<String, String> customHeaders = new HashMap<>();
                customHeaders.put("Authorization", "Basic " + AuthenticationHelper.createBasicAuthHeader());
                builder.customHeaders(customHeaders);
            }
            
            this.embeddingModel = builder.build();
                
        } catch (Exception e) {
            log.warn("Failed to initialize embedding model for similar patterns retrieval", e);
            this.embeddingModel = null;
        }
    }
}