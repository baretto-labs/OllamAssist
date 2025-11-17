package fr.baretto.ollamassist.completion;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import fr.baretto.ollamassist.auth.AuthenticationHelper;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Optimized version of LightModelAssistant with connection pooling,
 * caching, and performance improvements for code completion.
 */
@Slf4j
public class OptimizedLightModelAssistant {
    
    private static final ConcurrentHashMap<String, ModelConnection> connectionPool = new ConcurrentHashMap<>();
    private static final int MAX_POOL_SIZE = 3;
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration KEEP_ALIVE = Duration.ofMinutes(5);
    
    /**
     * Gets or creates an optimized service connection for the current settings.
     */
    @NotNull
    public static Service getOptimizedService() {
        OllamAssistSettings settings = OllamAssistSettings.getInstance();
        String connectionKey = generateConnectionKey(settings);
        
        ModelConnection connection = connectionPool.compute(connectionKey, (key, existing) -> {
            if (existing != null && existing.isValid()) {
                existing.updateLastUsed();
                return existing;
            }
            
            // Clean up old connection if exists
            if (existing != null) {
                existing.dispose();
            }
            
            // Create new optimized connection
            return createOptimizedConnection(settings);
        });
        
        // Clean up old connections periodically
        cleanupOldConnections();
        
        return connection.service;
    }
    
    /**
     * Asynchronous completion with timeout and cancellation support.
     */
    @NotNull
    public static CompletableFuture<String> completeAsync(
            @NotNull String context,
            @NotNull String extension,
            @Nullable String projectContext,
            @Nullable String similarPatterns) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Service service = getOptimizedService();
                return service.complete(context, extension, projectContext, similarPatterns);
            } catch (Exception e) {
                log.warn("Enhanced completion failed, trying basic", e);
                return getOptimizedService().completeBasic(context, extension);
            }
        }).orTimeout(CONNECTION_TIMEOUT.getSeconds(), TimeUnit.SECONDS);
    }
    
    /**
     * Asynchronous basic completion.
     */
    @NotNull
    public static CompletableFuture<String> completeBasicAsync(@NotNull String context, @NotNull String extension) {
        return CompletableFuture.supplyAsync(() -> 
            getOptimizedService().completeBasic(context, extension)
        ).orTimeout(CONNECTION_TIMEOUT.getSeconds(), TimeUnit.SECONDS);
    }
    
    /**
     * Creates an optimized model connection with performance tuned parameters.
     */
    @NotNull
    private static ModelConnection createOptimizedConnection(@NotNull OllamAssistSettings settings) {
        log.debug("Creating optimized connection for model: {}", settings.getCompletionModelName());
        
        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder()
            .baseUrl(settings.getCompletionOllamaUrl())
            .modelName(settings.getCompletionModelName())
            .temperature(0.1)                    // Lower temperature for more deterministic completions
            .topK(20)                           // Reduced for more focused completions
            .topP(0.8)                          // Balanced creativity vs consistency
            .timeout(CONNECTION_TIMEOUT)        // Shorter timeout for responsiveness
            .logRequests(false)                 // Disable request logging for performance
            .logResponses(false);               // Disable response logging for performance
        
        // Add authentication if configured
        if (AuthenticationHelper.isAuthenticationConfigured()) {
            Map<String, String> customHeaders = new HashMap<>();
            customHeaders.put("Authorization", String.format("Basic %s", AuthenticationHelper.createBasicAuthHeader()));
            builder.customHeaders(customHeaders);
        }
        
        OllamaChatModel model = builder.build();
        
        Service service = AiServices.builder(Service.class)
            .chatModel(model)
            .build();
        
        return new ModelConnection(service, System.currentTimeMillis());
    }
    
    /**
     * Generates a unique key for connection pooling based on settings.
     */
    @NotNull
    private static String generateConnectionKey(@NotNull OllamAssistSettings settings) {
        return String.format("%s|%s", 
            settings.getCompletionOllamaUrl(), 
            settings.getCompletionModelName());
    }
    
    /**
     * Cleans up old unused connections to prevent memory leaks.
     */
    private static void cleanupOldConnections() {
        if (connectionPool.size() <= MAX_POOL_SIZE) {
            return;
        }
        
        long cutoffTime = System.currentTimeMillis() - KEEP_ALIVE.toMillis();
        
        connectionPool.entrySet().removeIf(entry -> {
            ModelConnection connection = entry.getValue();
            if (connection.lastUsed < cutoffTime) {
                connection.dispose();
                log.debug("Cleaned up old connection: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * Disposes all connections and clears the pool.
     */
    public static void disposeAll() {
        connectionPool.values().forEach(ModelConnection::dispose);
        connectionPool.clear();
        log.debug("Disposed all model connections");
    }
    
    /**
     * Gets connection pool statistics for monitoring.
     */
    @NotNull
    public static ConnectionPoolStats getPoolStats() {
        long activeConnections = connectionPool.values().stream()
            .mapToLong(conn -> conn.isValid() ? 1 : 0)
            .sum();
            
        return new ConnectionPoolStats(
            connectionPool.size(),
            activeConnections,
            MAX_POOL_SIZE
        );
    }
    
    /**
     * Represents a cached model connection.
     */
    private static class ModelConnection {
        final Service service;
        volatile long lastUsed;
        
        ModelConnection(Service service, long lastUsed) {
            this.service = service;
            this.lastUsed = lastUsed;
        }
        
        void updateLastUsed() {
            this.lastUsed = System.currentTimeMillis();
        }
        
        boolean isValid() {
            return System.currentTimeMillis() - lastUsed < KEEP_ALIVE.toMillis();
        }
        
        void dispose() {
            // Model connections are managed by LangChain4J, no explicit disposal needed
        }
    }
    
    /**
     * Connection pool statistics.
     */
    public static class ConnectionPoolStats {
        public final int totalConnections;
        public final long activeConnections;
        public final int maxConnections;
        
        public ConnectionPoolStats(int totalConnections, long activeConnections, int maxConnections) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.maxConnections = maxConnections;
        }
        
        @Override
        public String toString() {
            return String.format("ConnectionPool{total=%d, active=%d, max=%d}", 
                totalConnections, activeConnections, maxConnections);
        }
    }
    
    /**
     * Optimized AI service interface for code completion.
     */
    public interface Service {
        @UserMessage("""
                You are an expert code completion assistant specialized in contextual, intelligent suggestions.
                
                **ANALYSIS PHASE:**
                1. **Language Context**: Based on file extension {{extension}}, apply language-specific patterns
                2. **Code Structure**: Analyze indentation, bracing style, naming conventions from the immediate context
                3. **Scope Context**: Determine if you're in class/method/block scope from the provided context
                4. **Intent Recognition**: Identify what the developer is likely trying to accomplish
                
                **COMPLETION RULES:**
                1. **Contextual Awareness**: Use provided project context and similar patterns to inform your completion
                2. **Minimal Precision**: Provide ONLY the immediate next logical continuation
                3. **Syntactic Correctness**: Ensure proper syntax, balanced braces, required semicolons
                4. **Consistent Style**: Match existing code style (spacing, naming, patterns)
                5. **No Repetition**: Never repeat any part of the provided context
                
                **CONTEXT SOURCES:**
                - **Immediate Context**: {{context}}
                {{#projectContext}}
                - **Project Context**: {{projectContext}}
                {{/projectContext}}
                {{#similarPatterns}}
                - **Similar Code Patterns**: {{similarPatterns}}
                {{/similarPatterns}}
                
                **OUTPUT FORMAT:**
                ```{{extension}}
                <your_completion_here>
                ```
                
                Provide ONLY the completion that logically follows the context.
                """)
        String complete(@V("context") String context, 
                       @V("extension") String fileExtension,
                       @V("projectContext") String projectContext,
                       @V("similarPatterns") String similarPatterns);
        
        @UserMessage("""  
                You are an expert software developer specializing in writing clean, concise, and accurate code.
                
                Your task is to provide the **next immediate continuation** of a given code snippet while adhering strictly to the following guidelines:
                
                ### **Guidelines:**
                1. **Syntactically Correct:** Ensure the code has proper syntax (e.g., balanced braces `{}`, proper indentation, required semicolons, etc.).
                2. **Minimal and Contextual:** Only provide the minimal lines needed to logically continue the snippet based on the provided context.
                3. **Strictly Code Only:** The response must only include valid code wrapped in triple backticks.
                4. **No Repetition or Modification:** Do not repeat or modify any part of the provided context.
                5. **One Logical Completion:** Provide only a single, logical continuation.
                
                ### **Context:**
                {{context}}
                
                ### **Output Format:**
                ```{{extension}}
                <completed_code>
                ```
                """)
        String completeBasic(@V("context") String context, @V("extension") String fileExtension);
    }
}