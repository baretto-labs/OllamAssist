package fr.baretto.ollamassist.prerequiste;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.HttpRequests;
import fr.baretto.ollamassist.ai.AutocompleteService;
import fr.baretto.ollamassist.chat.askfromcode.EditorListener;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.events.ModelAvailableNotifier;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

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

    public void loadModels(Project project) {
        new Task.Backgroundable(project, "Ollamassist is starting ...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                EditorListener.attachListeners();

                project.getService(OllamaService.class).init();
                AutocompleteService.get();

                ApplicationManager.getApplication()
                        .getMessageBus()
                        .syncPublisher(ModelAvailableNotifier.TOPIC)
                        .onModelAvailable();
            }
        }.queue();
    }
}