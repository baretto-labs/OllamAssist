package fr.baretto.ollamassist.chat.rag.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.rag.query.Query;
import fr.baretto.ollamassist.chat.rag.DuckDuckGoContentRetriever;
import fr.baretto.ollamassist.setting.OllamAssistSettings;

import java.util.stream.Collectors;

public class WebSearchTool {

    private final DuckDuckGoContentRetriever duckDuckGo = new DuckDuckGoContentRetriever(2);

    @Tool(name = "DuckDuckGoSearch", value = "This tool can be used to perform web searches using DuckDuckGo," +
            " particularly when seeking information about recent events.")
    public String searchOnDuckDuckGo(String query) {
        if (OllamAssistSettings.getInstance().webSearchEnabled()) {
            return duckDuckGo.retrieve(new Query(query)).stream()
                    .map(segment -> "Web search result: " + segment.textSegment().text())
                    .collect(Collectors.joining("\n\n"));
        }
        return "NOTHING";
    }
}
