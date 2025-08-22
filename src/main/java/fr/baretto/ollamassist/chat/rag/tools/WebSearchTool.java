package fr.baretto.ollamassist.chat.rag.tools;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.rag.query.Query;
import fr.baretto.ollamassist.chat.rag.DuckDuckGoContentRetriever;

import java.util.stream.Collectors;

public class WebSearchTool {

    private final DuckDuckGoContentRetriever duckDuckGo = new DuckDuckGoContentRetriever(2);

    @Tool(name = "DuckDuckGoSearch", value = "This tool can be used to perform web searches using DuckDuckGo," +
            " particularly when seeking information about recent events.")
    public String searchOnDuckDuckGo(String query) {
        try {
            return duckDuckGo.retrieve(new Query(query)).stream()
                    .map(segment -> "Web search result: " + segment.textSegment().text())
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception exception) {
            ApplicationManager.getApplication().getMessageBus().syncPublisher(Notifications.TOPIC)
                    .notify(new Notification(
                            "OllamAssist",
                            "Web search unavailable",
                            "Web search is currently unavailable because there is no internet connection. Please check your network settings and try again.",
                            NotificationType.INFORMATION
                    ));
            return "web search not available";
        }
    }
}
