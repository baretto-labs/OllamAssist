package fr.baretto.ollamassist.chat.rag;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import fr.baretto.ollamassist.setting.OllamAssistSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * ContextRetriever is a custom {@link ContentRetriever} for OllamAssist.
 *
 * <p>
 * Its purpose is to fetch relevant context for a given {@link Query} from multiple sources:
 * <ul>
 *     <li>The main {@link ContentRetriever}, typically backed by an embedding store or an internal RAG engine.</li>
 *     <li>The user's workspace ({@link WorkspaceContextRetriever}) to include context already present in the project.</li>
 *     <li>An external web search via {@link DuckDuckGoContentRetriever}, if {@link OllamAssistSettings#webSearchEnabled()} is enabled.</li>
 * </ul>
 * </p>
 *
 * <p>
 * Calls to these sources are executed in parallel using {@link CompletableFuture} and a dedicated
 * {@link ExecutorService}. This parallelism provides several benefits:
 * <ul>
 *     <li>Reduces total retrieval time by running multiple sources simultaneously.</li>
 *     <li>Prevents blocking the UI or calling thread while waiting for responses.</li>
 *     <li>Allows a global timeout of 2 seconds across all sources.</li>
 * </ul>
 * </p>
 *
 * <p>
 * Results from each source are merged into a single list, avoiding duplicates (based on {@link Content} text),
 * and filtering out content that is too short (less than 30 characters).
 * </p>
 *
 * <p>
 * Error handling is robust:
 * <ul>
 *     <li>Timeouts: if the combined calls exceed 2 seconds, a warning is shown.</li>
 *     <li>InternalServerException: embedding model errors are reported with details.</li>
 *     <li>Other exceptions: any unexpected error is notified to the user.</li>
 * </ul>
 * </p>
 *
 * <p>
 * Important: The {@link ExecutorService} is not shut down automatically within this class,
 * so that {@link #retrieve(Query)} can be called multiple times without triggering
 * {@link java.util.concurrent.RejectedExecutionException}. It can be properly shut down via a dedicated
 * method when the plugin is unloaded or the service is disposed.
 * </p>
 */
public class ContextRetriever implements ContentRetriever {

    private static final String NOTIFICATION_GROUP_ID = "OllamAssist";


    private final ContentRetriever contentRetriever;
    private final WorkspaceContextRetriever workspaceContextProvider;
    private final OllamAssistSettings settings;
    private final DuckDuckGoContentRetriever webSearchContentRetriever;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    public ContextRetriever(ContentRetriever contentRetriever, Project project) {
        this.contentRetriever = contentRetriever;
        this.workspaceContextProvider = project.getService(WorkspaceContextRetriever.class);
        this.webSearchContentRetriever = new DuckDuckGoContentRetriever(2);
        this.settings = OllamAssistSettings.getInstance();
    }

    public ContextRetriever(ContentRetriever contentRetriever, WorkspaceContextRetriever workspaceProvider,
                            DuckDuckGoContentRetriever webRetriever, OllamAssistSettings settings) {
        this.contentRetriever = contentRetriever;
        this.workspaceContextProvider = workspaceProvider;
        this.webSearchContentRetriever = webRetriever;
        this.settings = settings;
    }


    @Override
    public List<Content> retrieve(Query query) {

        try {
            CompletableFuture<List<Content>> retrieverFuture =
                    CompletableFuture.supplyAsync(() -> contentRetriever.retrieve(query), executor);

            CompletableFuture<List<Content>> workspaceFuture =
                    CompletableFuture.supplyAsync(() ->
                            workspaceContextProvider.get().stream()
                                    .filter(content -> content != null
                                            && isRelevant(content)
                                            && !containsContent(new ArrayList<>(), content))
                                    .toList(), executor);

            CompletableFuture<List<Content>> webFuture = CompletableFuture.completedFuture(Collections.emptyList());
            if (settings.webSearchEnabled()) {
                webFuture = CompletableFuture.supplyAsync(() -> webSearchContentRetriever.retrieve(query), executor);
            }

            CompletableFuture<Void> allDone =
                    CompletableFuture.allOf(retrieverFuture, workspaceFuture, webFuture);

            allDone.get(2, TimeUnit.SECONDS);

            List<Content> results = new ArrayList<>(safeGet(retrieverFuture));
            results.addAll(safeGet(workspaceFuture).stream()
                    .filter(content -> !containsContent(results, content))
                    .toList());
            results.addAll(safeGet(webFuture).stream()
                    .filter(content -> !containsContent(results, content))
                    .toList());

            return results;

        } catch (TimeoutException e) {
            Notifications.Bus.notify(new Notification(
                    NOTIFICATION_GROUP_ID,
                    "Timeout",
                    "Context retrieval took longer than 2 seconds and was aborted.",
                    NotificationType.WARNING
            ));
            return Collections.emptyList();
        } catch (InternalServerException e) {
            String modelName = settings.getEmbeddingModelName();
            String url = settings.getEmbeddingOllamaUrl();
            String serverResponse = e.getMessage();
            String errorMessage = String.format(
                    "The selected embedding model '%s' at '%s' does not support embeddings.<br>Server response: %s<br>Please select a different model in the settings.",
                    modelName, url, serverResponse
            );
            Notifications.Bus.notify(new Notification(NOTIFICATION_GROUP_ID, "Model error", errorMessage, NotificationType.ERROR));
            return Collections.emptyList();
        } catch (Exception e) {
            Notifications.Bus.notify(new Notification(
                    NOTIFICATION_GROUP_ID,
                    "Error",
                    "An unexpected error occurred while retrieving context.",
                    NotificationType.ERROR
            ));
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }
    }

    private <T> List<T> safeGet(CompletableFuture<List<T>> future) {
        try {
            return future.getNow(Collections.emptyList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private boolean isRelevant(Content content) {
        String text = content.textSegment().text();
        return text != null && text.length() > 30;
    }

    private boolean containsContent(List<Content> results, Content content) {
        return results.stream()
                .anyMatch(c -> c.textSegment().text().equals(content.textSegment().text()));
    }
}