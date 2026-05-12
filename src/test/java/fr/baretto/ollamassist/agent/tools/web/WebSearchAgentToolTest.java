package fr.baretto.ollamassist.agent.tools.web;

import fr.baretto.ollamassist.agent.tools.ToolResult;
import fr.baretto.ollamassist.chat.rag.DuckDuckGoContentRetriever;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WebSearchAgentToolTest {

    @Test
    void toolId_isWebSearch() {
        WebSearchAgentTool tool = new WebSearchAgentTool(mock(DuckDuckGoContentRetriever.class));
        assertThat(tool.toolId()).isEqualTo("WEB_SEARCH");
    }

    @Test
    void missingQuery_returnsFailure() {
        WebSearchAgentTool tool = new WebSearchAgentTool(mock(DuckDuckGoContentRetriever.class));
        // Settings unavailable in test → preCheck passes
        ToolResult r = tool.execute(Map.of());
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).contains("'query' is required");
    }

    @Test
    void retrieverReturnsResults_formattedInOutput() {
        DuckDuckGoContentRetriever retriever = mock(DuckDuckGoContentRetriever.class);
        when(retriever.searchRaw("latest jackson version"))
                .thenReturn(List.of(
                        new DuckDuckGoContentRetriever.SearchResult(
                                "Jackson 2.18 released",
                                "https://github.com/FasterXML/jackson",
                                "Version 2.18.0 released with ..."),
                        new DuckDuckGoContentRetriever.SearchResult(
                                "Maven Central",
                                "https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind",
                                "Latest: 2.18.0")));

        WebSearchAgentTool tool = new WebSearchAgentTool(retriever);
        ToolResult result = tool.execute(Map.of("query", "latest jackson version"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("Jackson 2.18 released");
        assertThat(result.getOutput()).contains("https://github.com/FasterXML/jackson");
        assertThat(result.getOutput()).contains("Version 2.18.0");
        assertThat(result.getOutput()).contains("---");
    }

    @Test
    void retrieverReturnsEmpty_noResultsMessage() {
        DuckDuckGoContentRetriever retriever = mock(DuckDuckGoContentRetriever.class);
        when(retriever.searchRaw(anyString())).thenReturn(List.of());

        WebSearchAgentTool tool = new WebSearchAgentTool(retriever);
        ToolResult result = tool.execute(Map.of("query", "obscure thing"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("No web results found");
        assertThat(result.getOutput()).contains("SEARCH_KNOWLEDGE");
    }

    @Test
    void retrieverThrows_returnsNoResults() {
        DuckDuckGoContentRetriever retriever = mock(DuckDuckGoContentRetriever.class);
        when(retriever.searchRaw(anyString())).thenThrow(new RuntimeException("network error"));

        WebSearchAgentTool tool = new WebSearchAgentTool(retriever);
        ToolResult result = tool.execute(Map.of("query", "something"));

        assertThat(result.isSuccess()).isTrue(); // graceful — no results, not failure
        assertThat(result.getOutput()).contains("No web results found");
    }

    @Test
    void retrieverNotCalledWithBlankQuery() {
        DuckDuckGoContentRetriever retriever = mock(DuckDuckGoContentRetriever.class);
        WebSearchAgentTool tool = new WebSearchAgentTool(retriever);

        tool.execute(Map.of("query", "  "));

        verifyNoInteractions(retriever);
    }
}
