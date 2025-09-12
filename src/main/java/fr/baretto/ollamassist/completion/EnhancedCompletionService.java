package fr.baretto.ollamassist.completion;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.actionSystem.IdeActions;

/**
 * Enhanced completion service that integrates caching, debouncing, and optimized model connections.
 * This is the main orchestrator for intelligent code completion.
 */
@Slf4j
public class EnhancedCompletionService {
    
    private final MultiSuggestionManager suggestionManager;
    private final EnhancedContextProvider contextProvider;
    private final SuggestionCache cache;
    private final CompletionDebouncer debouncer;
    
    // Configuration
    private static final int DEBOUNCE_DELAY_MS = 300;
    private static final String DEBOUNCE_KEY_PREFIX = "completion-";
    
    public EnhancedCompletionService(
            @NotNull MultiSuggestionManager suggestionManager,
            @NotNull EnhancedContextProvider contextProvider) {
        this.suggestionManager = suggestionManager;
        this.contextProvider = contextProvider;
        this.cache = new SuggestionCache();
        this.debouncer = new CompletionDebouncer();
    }
    
    /**
     * Handles a completion request with full optimization pipeline.
     */
    public void requestCompletion(@NotNull Editor editor) {
        System.err.println("🚀 [ERROR-DEBUG] EnhancedCompletionService.requestCompletion() called!");
        String debounceKey = DEBOUNCE_KEY_PREFIX + editor.hashCode();
        
        // Cancel any existing request for this editor
        debouncer.cancel(debounceKey);
        
        // Debounce the request to avoid multiple simultaneous calls
        System.err.println("⏱️ [ERROR-DEBUG] Debouncing completion request with key: " + debounceKey);
        debouncer.debounce(debounceKey, DEBOUNCE_DELAY_MS, () -> {
            System.err.println("🎯 [ERROR-DEBUG] Debounce timeout reached, calling executeCompletion()");
            executeCompletion(editor);
        });
    }
    
    /**
     * Executes the actual completion request with caching and optimization.
     */
    private void executeCompletion(@NotNull Editor editor) {
        System.err.println("🎬 [ERROR-DEBUG] executeCompletion() called!");
        ApplicationManager.getApplication().invokeLater(() -> {
            System.err.println("🎭 [ERROR-DEBUG] invokeLater() callback executing");
            // Show immediate loading feedback (get offset safely)
            int caretOffset = ApplicationManager.getApplication().runReadAction(
                (Computable<Integer>) () -> editor.getCaretModel().getOffset()
            );
            System.err.println("📍 [ERROR-DEBUG] Got caret offset: " + caretOffset);
            suggestionManager.showLoading(editor, caretOffset, "Generating suggestion");
            System.err.println("⏳ [ERROR-DEBUG] showLoading() called");
            
            System.err.println("🎪 [ERROR-DEBUG] About to create Task.Backgroundable");
            new Task.Backgroundable(editor.getProject(), "OllamAssist: Smart Code Completion", true) {
                
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    System.err.println("🏃 [ERROR-DEBUG] Task.Backgroundable.run() started!");
                    try {
                        System.err.println("🔍 [ERROR-DEBUG] About to call handleCompletionWithCache()");
                        handleCompletionWithCache(editor, indicator);
                        System.err.println("✅ [ERROR-DEBUG] handleCompletionWithCache() completed");
                    } catch (Exception e) {
                        System.err.println("❌ [ERROR-DEBUG] Exception in Task.run(): " + e.getMessage());
                        log.error("Completion request failed", e);
                        ApplicationManager.getApplication().invokeLater(() -> {
                            suggestionManager.disposeLoadingInlay();
                        });
                    }
                }
                
                @Override
                public void onCancel() {
                    System.err.println("🚫 [ERROR-DEBUG] Task cancelled!");
                    suggestionManager.disposeLoadingInlay();
                    log.debug("Completion request cancelled by user");
                }
                
                @Override
                public void onThrowable(@NotNull Throwable error) {
                    System.err.println("💥 [ERROR-DEBUG] Task threw error: " + error.getMessage());
                    suggestionManager.disposeLoadingInlay();
                    log.error("Completion request failed with error", error);
                }
            }.queue();
            System.err.println("📬 [ERROR-DEBUG] Task.queue() called");
        });
    }
    
    /**
     * Handles completion with caching layer.
     */
    private void handleCompletionWithCache(@NotNull Editor editor, @NotNull ProgressIndicator indicator) {
        System.err.println("🧠 [ERROR-DEBUG] handleCompletionWithCache() starting");
        indicator.setText("Building context...");
        
        // Build context for cache key generation and completion
        System.err.println("🏗️ [ERROR-DEBUG] About to call contextProvider.buildCompletionContextAsync()");
        CompletableFuture<CompletionContext> contextFuture = contextProvider.buildCompletionContextAsync(editor);
        System.err.println("🔮 [ERROR-DEBUG] contextFuture created, setting up thenAccept callback");
        
        contextFuture.thenAccept(completionContext -> {
            System.err.println("📥 [ERROR-DEBUG] contextFuture.thenAccept() callback executing");
            if (indicator.isCanceled()) {
                System.err.println("🚫 [ERROR-DEBUG] Indicator was cancelled, returning early");
                return;
            }
            
            System.err.println("🔑 [ERROR-DEBUG] About to generate cache key");
            // Generate cache key (generateCacheKey handles ReadAction internally)
            String cacheKey = cache.generateCacheKey(editor, completionContext.getImmediateContext());
            System.err.println("🔑 [ERROR-DEBUG] Cache key generated: " + cacheKey.substring(0, Math.min(8, cacheKey.length())));
            
            // Check cache first
            System.err.println("💾 [ERROR-DEBUG] Checking cache for key");
            String cachedSuggestion = cache.get(cacheKey);
            if (cachedSuggestion != null) {
                System.err.println("✅ [ERROR-DEBUG] Cache HIT! Using cached suggestion");
                log.info("✅ Using cached suggestion: '{}'", cachedSuggestion.substring(0, Math.min(50, cachedSuggestion.length())));
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!indicator.isCanceled()) {
                        int caretOffset = ApplicationManager.getApplication().runReadAction(
                            (Computable<Integer>) () -> editor.getCaretModel().getOffset()
                        );
                        suggestionManager.showSuggestion(editor, caretOffset, cachedSuggestion);
                        attachActionHandler(editor);
                    }
                });
                return;
            } else {
                System.err.println("❌ [ERROR-DEBUG] Cache MISS for key");
                log.info("❌ Cache miss for key: {}", cacheKey.substring(0, Math.min(8, cacheKey.length())));
            }
            
            // Generate new suggestion
            System.err.println("🤖 [ERROR-DEBUG] About to call generateNewSuggestion()");
            generateNewSuggestion(editor, completionContext, cacheKey, indicator);
            System.err.println("🤖 [ERROR-DEBUG] generateNewSuggestion() called");
            
        }).exceptionally(throwable -> {
            log.warn("Context building failed", throwable);
            indicator.setText("Context failed, using basic mode...");
            generateBasicSuggestion(editor, indicator);
            return null;
        });
    }
    
    /**
     * Generates a new suggestion using the optimized model assistant.
     */
    private void generateNewSuggestion(
            @NotNull Editor editor,
            @NotNull CompletionContext context,
            @NotNull String cacheKey,
            @NotNull ProgressIndicator indicator) {
        
        System.err.println("🎨 [ERROR-DEBUG] generateNewSuggestion() starting");
        indicator.setText("Generating AI suggestion...");
        
        System.err.println("🤖 [ERROR-DEBUG] About to call OptimizedLightModelAssistant.completeAsync()");
        System.err.println("🤖 [ERROR-DEBUG] Context: " + context.getImmediateContext().substring(0, Math.min(50, context.getImmediateContext().length())));
        System.err.println("🤖 [ERROR-DEBUG] File extension: " + context.getFileExtension());
        
        CompletableFuture<String> completionFuture = OptimizedLightModelAssistant.completeAsync(
            context.getImmediateContext(),
            context.getFileExtension(),
            context.getProjectContext(),
            context.getSimilarPatterns()
        );
        System.err.println("🔮 [ERROR-DEBUG] CompletableFuture created for AI completion");
        
        completionFuture.thenAccept(rawSuggestion -> {
            System.err.println("🎉 [ERROR-DEBUG] AI completion thenAccept() callback executing!");
            if (indicator.isCanceled()) {
                log.info("🚫 Enhanced suggestion cancelled by user");
                return;
            }
            
            log.info("🤖 Enhanced raw suggestion received: '{}'", rawSuggestion != null ? rawSuggestion.substring(0, Math.min(100, rawSuggestion.length())) : "null");
            
            try {
                String processedSuggestion = processSuggestion(rawSuggestion, editor);
                log.info("✨ Processed suggestion: '{}'", processedSuggestion);
                
                if (processedSuggestion.trim().isEmpty()) {
                    log.warn("⚠️ Processed suggestion is empty, falling back to basic");
                    generateBasicSuggestion(editor, indicator);
                    return;
                }
                
                // Cache the result
                cache.put(cacheKey, processedSuggestion);
                
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!indicator.isCanceled()) {
                        int caretOffset = ApplicationManager.getApplication().runReadAction(
                            (Computable<Integer>) () -> editor.getCaretModel().getOffset()
                        );
                        log.info("🎯 Showing enhanced suggestion at offset {} with content: '{}'", caretOffset, processedSuggestion);
                        suggestionManager.showSuggestion(editor, caretOffset, processedSuggestion);
                        attachActionHandler(editor);
                    }
                });
                
            } catch (Exception e) {
                log.error("❌ Suggestion processing failed", e);
                generateBasicSuggestion(editor, indicator);
            }
            
        }).exceptionally(throwable -> {
            log.error("❌ Enhanced suggestion generation failed", throwable);
            generateBasicSuggestion(editor, indicator);
            return null;
        });
    }
    
    /**
     * Fallback to basic suggestion generation.
     */
    private void generateBasicSuggestion(@NotNull Editor editor, @NotNull ProgressIndicator indicator) {
        indicator.setText("Generating basic suggestion...");
        
        String basicContext = getBasicContext(editor);
        String fileExtension = getFileExtension(editor);
        
        CompletableFuture<String> basicCompletionFuture = OptimizedLightModelAssistant.completeBasicAsync(
            basicContext, fileExtension
        );
        
        basicCompletionFuture.thenAccept(rawSuggestion -> {
            if (indicator.isCanceled()) {
                return;
            }
            
            try {
                String processedSuggestion = processSuggestion(rawSuggestion, editor);
                
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!indicator.isCanceled()) {
                        int caretOffset = ApplicationManager.getApplication().runReadAction(
                            (Computable<Integer>) () -> editor.getCaretModel().getOffset()
                        );
                        suggestionManager.showSuggestion(editor, caretOffset, processedSuggestion);
                        attachActionHandler(editor);
                    }
                });
                
            } catch (Exception e) {
                log.error("Basic suggestion processing failed", e);
                ApplicationManager.getApplication().invokeLater(() -> {
                    suggestionManager.disposeLoadingInlay();
                });
            }
        }).exceptionally(throwable -> {
            log.error("Basic suggestion generation failed", throwable);
            ApplicationManager.getApplication().invokeLater(() -> {
                suggestionManager.disposeLoadingInlay();
            });
            return null;
        });
    }
    
    /**
     * Processes raw LLM suggestion to extract clean code.
     */
    @NotNull
    private String processSuggestion(@NotNull String rawSuggestion, @NotNull Editor editor) {
        String suggestion = rawSuggestion;
        
        // Remove markdown code blocks
        if (suggestion.contains("```")) {
            suggestion = removeCodeBlockMarkers(suggestion);
        }
        
        // Remove any repetition of current line
        String lineStartContent = getLineStartContent(editor);
        if (!lineStartContent.trim().isEmpty() && suggestion.contains(lineStartContent.trim())) {
            int index = suggestion.indexOf(lineStartContent.trim());
            if (index != -1) {
                suggestion = suggestion.substring(index + lineStartContent.trim().length());
            }
        }
        
        return suggestion.trim();
    }
    
    /**
     * Removes markdown code block markers from suggestion.
     */
    @NotNull
    private String removeCodeBlockMarkers(@NotNull String suggestion) {
        String result = suggestion;
        
        // Remove opening code block
        result = result.replaceAll("```\\w*\\s*", "");
        
        // Remove closing code block
        int lastBackticks = result.lastIndexOf("```");
        if (lastBackticks != -1) {
            result = result.substring(0, lastBackticks);
        }
        
        return result.trim();
    }
    
    /**
     * Gets basic context for fallback completion.
     */
    @NotNull
    private String getBasicContext(@NotNull Editor editor) {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            int offset = editor.getCaretModel().getOffset();
            int start = Math.max(0, offset - 300);
            int end = Math.min(editor.getDocument().getTextLength(), offset);
            return editor.getDocument().getText().substring(start, end);
        });
    }
    
    /**
     * Gets file extension for language detection.
     */
    @NotNull
    private String getFileExtension(@NotNull Editor editor) {
        Project project = editor.getProject();
        if (project != null) {
            var files = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedFiles();
            if (files.length > 0 && files[0].getExtension() != null) {
                return files[0].getExtension();
            }
        }
        return "java"; // Default fallback
    }
    
    /**
     * Gets the content from line start to cursor.
     */
    @NotNull
    private String getLineStartContent(@NotNull Editor editor) {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            int offset = editor.getCaretModel().getOffset();
            int lineStart = editor.getDocument().getLineStartOffset(editor.getDocument().getLineNumber(offset));
            return editor.getDocument().getText().substring(lineStart, offset);
        });
    }
    
    /**
     * Attaches action handler for suggestion interaction using IntelliJ's action system.
     */
    private void attachActionHandler(@NotNull Editor editor) {
        System.err.println("🎪 [ERROR-DEBUG] attachActionHandler() called");
        
        EditorActionManager actionManager = EditorActionManager.getInstance();
        
        // Get the current Enter action handler
        EditorActionHandler originalHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
        
        // Create our custom handler that wraps the original
        SuggestionActionHandler suggestionHandler = new SuggestionActionHandler(suggestionManager, originalHandler);
        
        // Replace the Enter action handler temporarily
        actionManager.setActionHandler(IdeActions.ACTION_EDITOR_ENTER, suggestionHandler);
        
        System.err.println("🎪 [ERROR-DEBUG] Replaced Enter action handler with SuggestionActionHandler");
    }
    
    /**
     * Gets service statistics for monitoring and debugging.
     */
    @NotNull
    public ServiceStats getStats() {
        return new ServiceStats(
            cache.getStats(),
            OptimizedLightModelAssistant.getPoolStats(),
            debouncer.getPendingRequestCount()
        );
    }
    
    /**
     * Disposes the service and cleans up resources.
     */
    public void dispose() {
        debouncer.dispose();
        cache.clear();
        OptimizedLightModelAssistant.disposeAll();
        log.debug("EnhancedCompletionService disposed");
    }
    
    /**
     * Service statistics for monitoring.
     */
    public static class ServiceStats {
        public final SuggestionCache.CacheStats cacheStats;
        public final OptimizedLightModelAssistant.ConnectionPoolStats poolStats;
        public final int pendingRequests;
        
        public ServiceStats(SuggestionCache.CacheStats cacheStats, 
                           OptimizedLightModelAssistant.ConnectionPoolStats poolStats, 
                           int pendingRequests) {
            this.cacheStats = cacheStats;
            this.poolStats = poolStats;
            this.pendingRequests = pendingRequests;
        }
        
        @Override
        public String toString() {
            return String.format("EnhancedCompletionService Stats:\n- Cache: %s\n- Pool: %s\n- Pending: %d", 
                cacheStats, poolStats, pendingRequests);
        }
    }
}