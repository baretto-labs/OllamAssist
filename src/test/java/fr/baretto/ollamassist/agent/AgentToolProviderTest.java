package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.agent.tools.ToolRateLimiter;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import fr.baretto.ollamassist.agent.tools.files.EditFileTool;
import fr.baretto.ollamassist.agent.tools.files.WriteFileTool;
import fr.baretto.ollamassist.agent.tools.navigation.SearchCodeTool;
import fr.baretto.ollamassist.agent.tools.rag.SearchKnowledgeBaseTool;
import fr.baretto.ollamassist.agent.tools.web.WebSearchAgentTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolProviderTest {

    @Mock EditFileTool editFileTool;
    @Mock WriteFileTool writeFileTool;
    @Mock WebSearchAgentTool webSearchTool;
    @Mock SearchCodeTool searchCodeTool;
    @Mock SearchKnowledgeBaseTool searchKnowledgeTool;
    @Mock ToolRateLimiter rateLimiter;

    AgentToolProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        provider = new AgentToolProvider(
                editFileTool, writeFileTool, webSearchTool,
                searchCodeTool, searchKnowledgeTool, rateLimiter);
    }

    // -------------------------------------------------------------------------
    // editFile
    // -------------------------------------------------------------------------

    @Test
    void editFile_happyPath_returnsSuccessOutput() {
        when(editFileTool.execute(any())).thenReturn(ToolResult.success("File edited: src/Foo.java"));

        String result = provider.editFile("src/Foo.java", "old", "new", "false");

        assertThat(result).isEqualTo("File edited: src/Foo.java");
    }

    @Test
    void editFile_toolFailure_returnsErrorPrefix() {
        when(editFileTool.execute(any())).thenReturn(ToolResult.failure("File not found: src/Foo.java"));

        String result = provider.editFile("src/Foo.java", "old", "new", "false");

        assertThat(result).startsWith("ERROR: File not found");
    }

    @Test
    void editFile_nullPath_delegatesEmptyString() {
        when(editFileTool.execute(any())).thenReturn(ToolResult.failure("Parameter 'path' is required"));

        String result = provider.editFile(null, "old", "new", "false");

        assertThat(result).startsWith("ERROR:");
        verify(editFileTool).execute(Map.of("path", "", "search", "old", "replace", "new", "replaceAll", false));
    }

    @Test
    void editFile_rateLimitExceeded_returnsErrorWithoutCallingTool() {
        when(rateLimiter.tryAcquire("FILE_EDIT")).thenReturn(false);

        String result = provider.editFile("src/Foo.java", "old", "new", "false");

        assertThat(result).startsWith("ERROR:").contains("rate limit");
        verify(editFileTool, org.mockito.Mockito.never()).execute(any());
    }

    @Test
    void editFile_replaceAllTrue_passesBooleanTrue() {
        when(editFileTool.execute(any())).thenReturn(ToolResult.success("File edited: src/Foo.java"));

        provider.editFile("src/Foo.java", "old", "new", "true");

        verify(editFileTool).execute(Map.of("path", "src/Foo.java", "search", "old", "replace", "new", "replaceAll", true));
    }

    // -------------------------------------------------------------------------
    // writeFile
    // -------------------------------------------------------------------------

    @Test
    void writeFile_happyPath_returnsSuccessOutput() {
        when(writeFileTool.execute(any())).thenReturn(ToolResult.success("File created: src/Bar.java"));

        String result = provider.writeFile("src/Bar.java", "public class Bar {}");

        assertThat(result).isEqualTo("File created: src/Bar.java");
    }

    @Test
    void writeFile_toolFailure_returnsErrorPrefix() {
        when(writeFileTool.execute(any())).thenReturn(ToolResult.failure("File already exists"));

        String result = provider.writeFile("src/Bar.java", "content");

        assertThat(result).startsWith("ERROR: File already exists");
    }

    @Test
    void writeFile_nullContent_delegatesEmptyString() {
        when(writeFileTool.execute(any())).thenReturn(ToolResult.success("File created: src/Bar.java"));

        provider.writeFile("src/Bar.java", null);

        verify(writeFileTool).execute(Map.of("path", "src/Bar.java", "content", ""));
    }

    @Test
    void writeFile_rateLimitExceeded_returnsErrorWithoutCallingTool() {
        when(rateLimiter.tryAcquire("FILE_WRITE")).thenReturn(false);

        String result = provider.writeFile("src/Bar.java", "content");

        assertThat(result).startsWith("ERROR:").contains("rate limit");
        verify(writeFileTool, org.mockito.Mockito.never()).execute(any());
    }

    // -------------------------------------------------------------------------
    // searchWeb
    // -------------------------------------------------------------------------

    @Test
    void searchWeb_happyPath_returnsResults() {
        when(webSearchTool.execute(any())).thenReturn(ToolResult.success("Result 1\nResult 2"));

        String result = provider.searchWeb("LangChain4j tool use");

        assertThat(result).isEqualTo("Result 1\nResult 2");
    }

    @Test
    void searchWeb_disabled_returnsError() {
        when(webSearchTool.execute(any())).thenReturn(ToolResult.failure("Web search is disabled"));

        String result = provider.searchWeb("query");

        assertThat(result).startsWith("ERROR: Web search is disabled");
    }

    @Test
    void searchWeb_nullQuery_delegatesEmptyString() {
        when(webSearchTool.execute(any())).thenReturn(ToolResult.failure("Parameter 'query' is required"));

        provider.searchWeb(null);

        verify(webSearchTool).execute(Map.of("query", ""));
    }

    @Test
    void searchWeb_rateLimitExceeded_returnsErrorWithoutCallingTool() {
        when(rateLimiter.tryAcquire("WEB_SEARCH")).thenReturn(false);

        String result = provider.searchWeb("query");

        assertThat(result).startsWith("ERROR:").contains("rate limit");
        verify(webSearchTool, org.mockito.Mockito.never()).execute(any());
    }

    // -------------------------------------------------------------------------
    // searchWorkspace
    // -------------------------------------------------------------------------

    @Test
    void searchWorkspace_happyPath_returnsMatches() {
        when(searchCodeTool.execute(any())).thenReturn(ToolResult.success("src/Foo.java:12\n> OllamaService svc"));

        String result = provider.searchWorkspace("OllamaService");

        assertThat(result).contains("OllamaService");
    }

    @Test
    void searchWorkspace_noMatches_returnsSuccessMessage() {
        when(searchCodeTool.execute(any())).thenReturn(ToolResult.success("No matches found for: xyz123"));

        String result = provider.searchWorkspace("xyz123");

        assertThat(result).contains("No matches found");
    }

    @Test
    void searchWorkspace_rateLimitExceeded_returnsErrorWithoutCallingTool() {
        when(rateLimiter.tryAcquire("CODE_SEARCH")).thenReturn(false);

        String result = provider.searchWorkspace("query");

        assertThat(result).startsWith("ERROR:").contains("rate limit");
        verify(searchCodeTool, org.mockito.Mockito.never()).execute(any());
    }

    // -------------------------------------------------------------------------
    // searchKnowledgeBase
    // -------------------------------------------------------------------------

    @Test
    void searchKnowledgeBase_happyPath_returnsSegments() {
        when(searchKnowledgeTool.execute(any())).thenReturn(ToolResult.success("Segment: file indexing pipeline..."));

        String result = provider.searchKnowledgeBase("file indexing");

        assertThat(result).contains("file indexing pipeline");
    }

    @Test
    void searchKnowledgeBase_storeUnavailable_returnsError() {
        when(searchKnowledgeTool.execute(any())).thenReturn(ToolResult.failure("Knowledge base not available"));

        String result = provider.searchKnowledgeBase("query");

        assertThat(result).startsWith("ERROR: Knowledge base not available");
    }

    @Test
    void searchKnowledgeBase_rateLimitExceeded_returnsErrorWithoutCallingTool() {
        when(rateLimiter.tryAcquire("SEARCH_KNOWLEDGE")).thenReturn(false);

        String result = provider.searchKnowledgeBase("query");

        assertThat(result).startsWith("ERROR:").contains("rate limit");
        verify(searchKnowledgeTool, org.mockito.Mockito.never()).execute(any());
    }

    // -------------------------------------------------------------------------
    // toObservation — empty success output
    // -------------------------------------------------------------------------

    @Test
    void emptySuccessOutput_returnsPlaceholder() {
        when(editFileTool.execute(any())).thenReturn(ToolResult.success(""));

        String result = provider.editFile("src/Foo.java", "old", "new", "false");

        assertThat(result).isEqualTo("(empty result)");
    }
}
