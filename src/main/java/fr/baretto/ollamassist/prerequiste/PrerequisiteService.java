package fr.baretto.ollamassist.prerequiste;

import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.HttpRequests;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static fr.baretto.ollamassist.chat.rag.RAGConstants.DEFAULT_EMBEDDING_MODEL;

@NoArgsConstructor
public class PrerequisiteService {

    public static final String PATH_TO_VERSION = "/api/version";
    public static final String PATH_TO_TAGS = "/api/tags";

    public CompletableFuture<Boolean> isOllamaRunningAsync(String url) {
        return isOllamaAttributeExists(url, PATH_TO_VERSION, s -> true);
    }

    public CompletableFuture<Boolean> isChatModelAvailableAsync(String url, String modelName) {
        return isOllamaAttributeExists(url, PATH_TO_TAGS, s -> s.contains(modelName));
    }

    public CompletableFuture<Boolean> isAutocompleteModelAvailableAsync(String url, String modelName) {
        return isOllamaAttributeExists(url, PATH_TO_TAGS, s -> s.contains(modelName));
    }

    public CompletableFuture<Boolean> isEmbeddingModelAvailableAsync(String url, String modelName) {
        // The model used was BgeSmallEnV15Quantized, which is provided by langchain4j. To avoid creating a blocking point for users utilizing it,
        // we should not perform verification through Ollama's endpoint call.
        // Perhaps we should consider migrating to another Ollama model in the future?
        if (DEFAULT_EMBEDDING_MODEL.equals(modelName)) {
            return CompletableFuture.completedFuture(true);
        }
        return isOllamaAttributeExists(url, PATH_TO_TAGS, s -> s.contains(modelName));
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