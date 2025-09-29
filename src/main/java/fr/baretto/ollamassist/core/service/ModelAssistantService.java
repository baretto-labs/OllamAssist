package fr.baretto.ollamassist.core.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import fr.baretto.ollamassist.completion.LightModelAssistant;
import fr.baretto.ollamassist.core.config.OllamAssistConfig;
import fr.baretto.ollamassist.core.model.ChatRequest;
import fr.baretto.ollamassist.core.model.CompletionRequest;
import fr.baretto.ollamassist.core.state.ApplicationState;
import fr.baretto.ollamassist.core.state.PluginState;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IntelliJ Application Service implementation of ModelAssistant.
 * Provides AI model interactions with state management and configuration integration.
 */
@Slf4j
@Service
public final class ModelAssistantService implements ModelAssistant {

    private final AtomicInteger chatRequests = new AtomicInteger(0);
    private final AtomicInteger completionRequests = new AtomicInteger(0);
    private final AtomicInteger commitRequests = new AtomicInteger(0);

    @Override
    public CompletableFuture<String> chat(@NotNull ChatRequest request) {
        chatRequests.incrementAndGet();
        log.debug("Processing chat request: {}", request.getMessage().substring(0, Math.min(50, request.getMessage().length())));

        // Get services
        ApplicationState appState = ApplicationManager.getApplication().getService(ApplicationState.class);
        OllamAssistConfig config = ApplicationManager.getApplication().getService(OllamAssistConfig.class);

        return CompletableFuture.supplyAsync(() -> {
            appState.setState(PluginState.PROCESSING);
            try {
                // Use LightModelAssistant for basic chat - for complex chat use OllamaService instead
                // Note: This is for simple chat requests. Complex conversations should use OllamaService with RAG
                String response = "Basic chat response: " + request.getMessage() + " (Use OllamaService for full chat experience)";

                if (config.isDebugMode()) {
                    log.debug("Basic chat response generated: {} chars", response.length());
                }

                return response;
            } finally {
                appState.setState(PluginState.IDLE);
            }
        });
    }

    @Override
    public CompletableFuture<String> complete(@NotNull CompletionRequest request) {
        completionRequests.incrementAndGet();
        log.debug("Processing completion request for context: {}", request.getContext().substring(0, Math.min(50, request.getContext().length())));

        // Get services
        ApplicationState appState = ApplicationManager.getApplication().getService(ApplicationState.class);
        OllamAssistConfig config = ApplicationManager.getApplication().getService(OllamAssistConfig.class);

        return CompletableFuture.supplyAsync(() -> {
            appState.setState(PluginState.PROCESSING);
            try {
                // Delegate to LightModelAssistant for fast completion
                String completion = LightModelAssistant.get()
                        .completeBasic(request.getContext(), request.getFileExtension());

                if (config.isDebugMode()) {
                    log.debug("Completion generated: {} chars", completion.length());
                }

                return completion;
            } finally {
                appState.setState(PluginState.IDLE);
            }
        });
    }

    @Override
    public CompletableFuture<String> writeCommitMessage(@NotNull String gitDiff) {
        commitRequests.incrementAndGet();
        log.debug("Processing commit message request for diff: {} chars", gitDiff.length());

        // Get services
        ApplicationState appState = ApplicationManager.getApplication().getService(ApplicationState.class);
        OllamAssistConfig config = ApplicationManager.getApplication().getService(OllamAssistConfig.class);

        return CompletableFuture.supplyAsync(() -> {
            appState.setState(PluginState.PROCESSING);
            try {
                // Delegate to LightModelAssistant for fast commit message generation
                String commitMessage = LightModelAssistant.get()
                        .writecommitMessage(gitDiff);

                if (config.isDebugMode()) {
                    log.debug("Commit message generated: {}", commitMessage);
                }

                return commitMessage;
            } finally {
                appState.setState(PluginState.IDLE);
            }
        });
    }

    @Override
    public String createWebSearchQuery(String userInput) {
        log.debug("Creating web search query for input: {}", userInput.substring(0, Math.min(50, userInput.length())));

        // Delegate to LightModelAssistant for web search query generation
        return LightModelAssistant.get().createWebSearchQuery(userInput);
    }

    @Override
    public RequestStats getRequestStats() {
        return new RequestStats() {
            @Override
            public int getTotalRequests() {
                return chatRequests.get() + completionRequests.get() + commitRequests.get();
            }

            @Override
            public int getChatRequests() {
                return chatRequests.get();
            }

            @Override
            public int getCompletionRequests() {
                return completionRequests.get();
            }

            @Override
            public int getCommitMessageRequests() {
                return commitRequests.get();
            }
        };
    }
}