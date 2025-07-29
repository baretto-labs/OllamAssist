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

public class ContextRetriever implements ContentRetriever {

    private final ContentRetriever contentRetriever;
    private final FocusContextProvider focusContextProvider;
    private final OllamAssistSettings settings;


    public ContextRetriever(ContentRetriever contentRetriever, Project project) {
        this.contentRetriever = contentRetriever;
        this.focusContextProvider = new FocusedWindowContextProvider(project);
        this.settings = OllamAssistSettings.getInstance();
    }


    @Override
    public List<Content> retrieve(Query query) {
        try {
            List<Content> results = new ArrayList<>(contentRetriever.retrieve(query));

            Content cursorContext = focusContextProvider.get(query);

            if (cursorContext != null && isRelevant(cursorContext) && !containsContent(results, cursorContext)) {
                results.add(cursorContext);
            }

            return results;
        } catch (InternalServerException e) {
            String modelName = settings.getEmbeddingModelName();
            String url = settings.getEmbeddingOllamaUrl();
            String serverResponse = e.getMessage();
            String errorMessage = String.format(
                "The selected embedding model '%s' at '%s' does not support embeddings.<br>Server response: %s<br>Please select a different model in the settings.",
                modelName, url, serverResponse
            );
            Notifications.Bus.notify(new Notification("OllamAssist", "Model error", errorMessage, NotificationType.ERROR));
            return Collections.emptyList();
        } catch (Exception e) {
            Notifications.Bus.notify(new Notification("OllamAssist", "Error", "An unexpected error occurred while retrieving context.", NotificationType.ERROR));
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