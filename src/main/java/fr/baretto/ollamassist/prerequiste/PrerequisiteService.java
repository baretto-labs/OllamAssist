package fr.baretto.ollamassist.prerequiste;

import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.HttpRequests;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

@NoArgsConstructor
public class PrerequisiteService {

    public static final String PATH_TO_VERSION = "/api/version";
    public static final String PATH_TO_TAGS = "/api/tags";

    public CompletableFuture<Boolean> isOllamaRunningAsync() {
        return isOllamaAttributeExists(PATH_TO_VERSION, s -> true);
    }

    public CompletableFuture<Boolean> isChatModelAvailableAsync() {
        return isOllamaAttributeExists(PATH_TO_TAGS, s -> s.contains(OllamAssistSettings.getInstance().getChatModelName()));
    }

    public CompletableFuture<Boolean> isAutocompleteModelAvailableAsync() {
        return isOllamaAttributeExists(PATH_TO_TAGS, s -> s.contains(OllamAssistSettings.getInstance().getCompletionModelName()));
    }

    public CompletableFuture<Boolean> isEmbeddingModelAvailableAsync() {
        return isOllamaAttributeExists(PATH_TO_TAGS, s -> s.contains(OllamAssistSettings.getInstance().getEmbeddingModelName()));
    }

    private CompletableFuture<Boolean> isOllamaAttributeExists(String endpoint, Predicate<String> check) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = HttpRequests.request(OllamAssistSettings.getInstance().getOllamaUrl() + endpoint)
                        .connectTimeout(3000)
                        .readTimeout(3000)
                        .readString();
                return check.test(response);
            } catch (IOException ignored) {
                return false;
            }
        }, AppExecutorUtil.getAppExecutorService());
    }

    public boolean allPrerequisitesAreAvailable(Boolean ollamaReady, Boolean chatModelReady, Boolean autocompleteModelReady) {
        return ollamaReady && chatModelReady && Boolean.TRUE.equals(autocompleteModelReady);
    }
}