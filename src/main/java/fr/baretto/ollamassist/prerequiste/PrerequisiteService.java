package fr.baretto.ollamassist.prerequiste;

import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.HttpRequests;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static fr.baretto.ollamassist.chat.rag.RAGConstants.DEFAULT_EMBEDDING_MODEL;

@Slf4j
@NoArgsConstructor
public class PrerequisiteService {

    public static final String PATH_TO_VERSION = "/api/version";
    public static final String PATH_TO_TAGS = "/api/tags";
    private static final String FALLBACK_EMBEDDING_MODEL = "nomic-embed-text";

    public CompletableFuture<Boolean> isOllamaRunningAsync(String url) {
        return isOllamaAttributeExists(url, PATH_TO_VERSION, s -> true);
    }

    public CompletableFuture<Boolean> isChatModelAvailableAsync(String url, String modelName) {
        return isOllamaAttributeExists(url, PATH_TO_TAGS, s -> s.contains(modelName));
    }

    public CompletableFuture<Boolean> isAutocompleteModelAvailableAsync(String url, String modelName) {
        return isOllamaAttributeExists(url, PATH_TO_TAGS, s -> s.contains(modelName));
    }

    /**
     * @deprecated Use {@link #checkEmbeddingModelAsync(String, String)} instead for detailed status
     */
    @Deprecated
    public CompletableFuture<Boolean> isEmbeddingModelAvailableAsync(String url, String modelName) {
        return checkEmbeddingModelAsync(url, modelName)
                .thenApply(EmbeddingModelCheckResult::isUsable);
    }

    /**
     * Checks embedding model availability with detailed status.
     * For local DJL models, verifies that native libraries can be loaded.
     * See issue #145: https://github.com/baretto-labs/OllamAssist/issues/145
     *
     * @param url       Ollama server URL
     * @param modelName Model name to check
     * @return CompletableFuture with detailed check result
     */
    public CompletableFuture<EmbeddingModelCheckResult> checkEmbeddingModelAsync(String url, String modelName) {
        return CompletableFuture.supplyAsync(() -> {
            // Case 1: Local DJL model (BgeSmallEnV15Quantized)
            if (DEFAULT_EMBEDDING_MODEL.equals(modelName)) {
                return checkLocalEmbeddingModel(url);
            }

            // Case 2: Ollama model configured
            try {
                String response = HttpRequests.request(url + PATH_TO_TAGS)
                        .connectTimeout(3000)
                        .readTimeout(3000)
                        .readString();

                if (response.contains(modelName)) {
                    return EmbeddingModelCheckResult.ollamaAvailable();
                } else {
                    return EmbeddingModelCheckResult.notAvailable();
                }
            } catch (IOException e) {
                log.warn("Failed to check Ollama model availability: {}", e.getMessage());
                return EmbeddingModelCheckResult.notAvailable();
            }
        }, AppExecutorUtil.getAppExecutorService());
    }

    /**
     * Checks if local DJL embedding model can be loaded.
     * Attempts to load DJL tokenizer class to detect native library issues.
     *
     * @param ollamaUrl URL to check fallback Ollama model
     * @return Check result with fallback status
     */
    private EmbeddingModelCheckResult checkLocalEmbeddingModel(String ollamaUrl) {
        try {
            // Try to load DJL tokenizer class to detect UnsatisfiedLinkError early
            Class.forName("ai.djl.huggingface.tokenizers.jni.TokenizersLibrary");

            // Try to trigger native library loading without creating full model
            // This is a lightweight check to detect Windows/platform issues
            testDJLNativeLibraries();

            return EmbeddingModelCheckResult.localAvailable();
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError | ClassNotFoundException e) {
            log.warn("Local DJL embedding model failed to load (native library error). " +
                    "Checking Ollama fallback availability. Error: {}", e.getMessage());

            // Check if fallback Ollama model is available
            boolean fallbackAvailable = checkOllamaModelSync(ollamaUrl, FALLBACK_EMBEDDING_MODEL);
            return EmbeddingModelCheckResult.localFailedWithFallback(fallbackAvailable);
        }
    }

    /**
     * Tests DJL native library loading without creating a full model instance.
     * This lightweight check detects platform-specific issues (e.g., Windows).
     */
    private void testDJLNativeLibraries() {
        try {
            // Attempt to access a static method that triggers native lib loading
            // This will throw UnsatisfiedLinkError if libraries are missing
            Class<?> tokenizerClass = Class.forName("ai.djl.huggingface.tokenizers.jni.TokenizersLibrary");
            // Just loading the class is enough to trigger static initializers
            tokenizerClass.getDeclaredMethods(); // Force class initialization
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Synchronous check for Ollama model availability (used by checkLocalEmbeddingModel).
     *
     * @param url       Ollama server URL
     * @param modelName Model name to check
     * @return true if model is available
     */
    private boolean checkOllamaModelSync(String url, String modelName) {
        try {
            String response = HttpRequests.request(url + PATH_TO_TAGS)
                    .connectTimeout(3000)
                    .readTimeout(3000)
                    .readString();
            return response.contains(modelName);
        } catch (IOException e) {
            log.debug("Failed to check fallback model availability: {}", e.getMessage());
            return false;
        }
    }

    private CompletableFuture<Boolean> isOllamaAttributeExists(String url, String endpoint, Predicate<String> check) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = HttpRequests.request(url + endpoint)
                        .connectTimeout(3000)
                        .readTimeout(3000)
                        .readString();
                return check.test(response);
            } catch (IOException ignored) {
                return false;
            }
        }, AppExecutorUtil.getAppExecutorService());
    }

}