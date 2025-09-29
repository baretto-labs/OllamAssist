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
import fr.baretto.ollamassist.core.service.ModelAssistantService;
import fr.baretto.ollamassist.events.ModelAvailableNotifier;
import fr.baretto.ollamassist.prerequiste.PrerequisiteService;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class OllamAssistStartup implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super kotlin.Unit> continuation) {
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
                Notifications.Bus.notify(new Notification("OllamAssist", "Ollama Not Running", "Ollama is not running at " + settings.getChatOllamaUrl() + ". Chat features will be disabled.", NotificationType.WARNING), project);
                chatModelAvailableFuture.complete(false);
            }
        });

        completionOllamaRunningFuture.thenAccept(completionRunning -> {
            if (completionRunning) {
                prerequisiteService.isAutocompleteModelAvailableAsync(settings.getCompletionOllamaUrl(), settings.getCompletionModelName())
                        .thenAccept(completionModelAvailableFuture::complete);
            } else {
                Notifications.Bus.notify(new Notification("OllamAssist", "Ollama Not Running", "Ollama is not running at " + settings.getCompletionOllamaUrl() + ". Completion features will be disabled.", NotificationType.WARNING), project);
                completionModelAvailableFuture.complete(false);
            }
        });

        embeddingOllamaRunningFuture.thenAccept(embeddingRunning -> {
            if (embeddingRunning) {
                prerequisiteService.isEmbeddingModelAvailableAsync(settings.getEmbeddingOllamaUrl(), settings.getEmbeddingModelName())
                        .thenAccept(embeddingModelAvailableFuture::complete);
            } else {
                Notifications.Bus.notify(new Notification("OllamAssist", "Ollama Not Running", "Ollama is not running at " + settings.getEmbeddingOllamaUrl() + ". Embedding features will be disabled.", NotificationType.WARNING), project);
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
                        // Initialize ModelAssistantService for fast operations
                        ApplicationManager.getApplication().getService(ModelAssistantService.class);

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