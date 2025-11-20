package fr.baretto.ollamassist;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import fr.baretto.ollamassist.chat.askfromcode.EditorListener;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.completion.LightModelAssistant;
import fr.baretto.ollamassist.events.ModelAvailableNotifier;
import fr.baretto.ollamassist.prerequiste.PrerequisiteService;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import fr.baretto.ollamassist.setting.SettingsMigrationService;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class OllamAssistStartup implements ProjectActivity {

    private static final String OLLAMA_NOT_RUNNING = "Ollama Not Running";
    public static final String OLLAM_ASSIST = "OllamAssist";
    private static final String OLLAMA_NOT_RUNNING_MESSAGE_TEMPLATE = "Ollama is not running at %s. %s features will be disabled.";
    private static final String CHAT_FEATURE = "Chat";
    private static final String COMPLETION_FEATURE = "Completion";
    private static final String EMBEDDING_FEATURE = "Embedding";

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super kotlin.Unit> continuation) {
        // Migrate settings from old OllamAssistSettings to new split settings
        SettingsMigrationService.getInstance().migrateIfNeeded();

        final PrerequisiteService prerequisiteService = ApplicationManager.getApplication().getService(PrerequisiteService.class);
        OllamAssistSettings settings = OllamAssistSettings.getInstance();

        CompletableFuture<Boolean> chatOllamaRunningFuture = prerequisiteService.isOllamaRunningAsync(settings.getChatOllamaUrl());
        CompletableFuture<Boolean> completionOllamaRunningFuture = prerequisiteService.isOllamaRunningAsync(settings.getCompletionOllamaUrl());
        CompletableFuture<Boolean> embeddingOllamaRunningFuture = prerequisiteService.isOllamaRunningAsync(settings.getEmbeddingOllamaUrl());


        CompletableFuture<Boolean> chatModelAvailableFuture = new CompletableFuture<>();
        CompletableFuture<Boolean> completionModelAvailableFuture = new CompletableFuture<>();
        CompletableFuture<Boolean> embeddingModelAvailableFuture = new CompletableFuture<>();

        chatOllamaRunningFuture.thenAccept(chatRunning -> {
            if (chatRunning) {
                prerequisiteService.isChatModelAvailableAsync(settings.getChatOllamaUrl(), settings.getChatModelName())
                        .thenAccept(chatModelAvailableFuture::complete);
            } else {
                String message = String.format(OLLAMA_NOT_RUNNING_MESSAGE_TEMPLATE, settings.getChatOllamaUrl(), CHAT_FEATURE);
                Notifications.Bus.notify(new Notification(OLLAM_ASSIST, OLLAMA_NOT_RUNNING, message, NotificationType.WARNING), project);
                chatModelAvailableFuture.complete(false);
            }
        });

        completionOllamaRunningFuture.thenAccept(completionRunning -> {
            if (completionRunning) {
                prerequisiteService.isAutocompleteModelAvailableAsync(settings.getCompletionOllamaUrl(), settings.getCompletionModelName())
                        .thenAccept(completionModelAvailableFuture::complete);
            } else {
                String message = String.format(OLLAMA_NOT_RUNNING_MESSAGE_TEMPLATE, settings.getCompletionOllamaUrl(), COMPLETION_FEATURE);
                Notifications.Bus.notify(new Notification(OLLAM_ASSIST, OLLAMA_NOT_RUNNING, message, NotificationType.WARNING), project);
                completionModelAvailableFuture.complete(false);
            }
        });

        embeddingOllamaRunningFuture.thenAccept(embeddingRunning -> {
            if (embeddingRunning) {
                prerequisiteService.isEmbeddingModelAvailableAsync(settings.getEmbeddingOllamaUrl(), settings.getEmbeddingModelName())
                        .thenAccept(embeddingModelAvailableFuture::complete);
            } else {
                String message = String.format(OLLAMA_NOT_RUNNING_MESSAGE_TEMPLATE, settings.getEmbeddingOllamaUrl(), EMBEDDING_FEATURE);
                Notifications.Bus.notify(new Notification(OLLAM_ASSIST, OLLAMA_NOT_RUNNING, message, NotificationType.WARNING), project);
                embeddingModelAvailableFuture.complete(false);
            }
        });

        // Now, wait for all model availability futures to complete
        CompletableFuture.allOf(
                chatModelAvailableFuture,
                completionModelAvailableFuture,
                embeddingModelAvailableFuture
        ).thenAccept(v -> {
            boolean allOllamaRunning = chatOllamaRunningFuture.join() && completionOllamaRunningFuture.join() && embeddingOllamaRunningFuture.join();
            boolean allModelsAvailable = chatModelAvailableFuture.join() && completionModelAvailableFuture.join() && embeddingModelAvailableFuture.join();

            if (allOllamaRunning && allModelsAvailable) {
                new Task.Backgroundable(project, "Ollamassist is starting ...", true) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        project.getService(OllamaService.class).init();
                        LightModelAssistant.get();

                        ApplicationManager.getApplication()
                                .getMessageBus()
                                .syncPublisher(ModelAvailableNotifier.TOPIC)
                                .onModelAvailable();
                    }
                }.queue();
                EditorListener.attachListeners();
            }
        });

        return null;
    }
}