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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the ReAct loop guards.
 *
 * <p>These tests verify the collaboration between {@link AgentToolProvider},
 * {@link ToolRateLimiter}, and the abort mechanism without requiring a real LLM.
 * They simulate the sequence of tool calls that the LLM would make during a
 * multi-step ReAct execution.
 */
class AgentReActLoopIntegrationTest {

    @Mock SearchCodeTool searchCodeTool;
    @Mock EditFileTool editFileTool;
    @Mock WriteFileTool writeFileTool;
    @Mock WebSearchAgentTool webSearchTool;
    @Mock SearchKnowledgeBaseTool searchKnowledgeTool;

    ToolRateLimiter rateLimiter;
    AgentToolProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        rateLimiter = new ToolRateLimiter();
        provider = new AgentToolProvider(
                editFileTool, writeFileTool, webSearchTool,
                searchCodeTool, searchKnowledgeTool, rateLimiter);
    }

    // -------------------------------------------------------------------------
    // Multi-step ReAct sequence — happy path
    // -------------------------------------------------------------------------

    @Test
    void multiStepSequence_searchThenEdit_allStepsSucceed() {
        when(searchCodeTool.execute(any())).thenReturn(ToolResult.success("src/Foo.java:12 — found OllamaService"));
        when(editFileTool.execute(any())).thenReturn(ToolResult.success("File edited: src/Foo.java"));

        // Step 1: LLM searches workspace
        String searchResult = provider.searchWorkspace("OllamaService");
        assertThat(searchResult).contains("found OllamaService");

        // Step 2: LLM edits based on search result
        String editResult = provider.editFile("src/Foo.java", "old code", "new code", "false");
        assertThat(editResult).isEqualTo("File edited: src/Foo.java");

        verify(searchCodeTool).execute(Map.of("query", "OllamaService"));
        verify(editFileTool).execute(Map.of("path", "src/Foo.java", "search", "old code", "replace", "new code", "replaceAll", false));
    }

    // -------------------------------------------------------------------------
    // Abort mechanism — MAX_TOOL_CALLS triggers abort flag
    // -------------------------------------------------------------------------

    @Test
    void abortAfterMaxToolCalls_allSubsequentCallsReturnAbortMessage() {
        when(searchCodeTool.execute(any())).thenReturn(ToolResult.success("result"));

        // Simulate FunctionCallingAgentService counting tool calls
        AtomicInteger callCount = new AtomicInteger(0);
        int maxCalls = FunctionCallingAgentService.MAX_TOOL_CALLS_PER_EXECUTION;

        // Make MAX_TOOL_CALLS calls — all should succeed
        for (int i = 0; i < maxCalls; i++) {
            String result = provider.searchWorkspace("query" + i);
            assertThat(result).isNotEmpty();
            if (callCount.incrementAndGet() >= maxCalls) {
                provider.abort(); // simulates what FunctionCallingAgentService does in onToolExecuted
            }
        }

        // After abort, ALL tools must return abort message without calling delegates
        String editAfterAbort = provider.editFile("src/Foo.java", "old", "new", "false");
        String writeAfterAbort = provider.writeFile("src/Bar.java", "content");
        String webAfterAbort = provider.searchWeb("query");
        String workspaceAfterAbort = provider.searchWorkspace("query");
        String knowledgeAfterAbort = provider.searchKnowledgeBase("concept");

        assertThat(editAfterAbort).startsWith("ERROR:").contains("maximum tool calls");
        assertThat(writeAfterAbort).startsWith("ERROR:").contains("maximum tool calls");
        assertThat(webAfterAbort).startsWith("ERROR:").contains("maximum tool calls");
        assertThat(workspaceAfterAbort).startsWith("ERROR:").contains("maximum tool calls");
        assertThat(knowledgeAfterAbort).startsWith("ERROR:").contains("maximum tool calls");

        // Delegates must not have been called after abort
        verify(editFileTool, never()).execute(any());
        verify(writeFileTool, never()).execute(any());
    }

    // -------------------------------------------------------------------------
    // Reset between executions — state is clean for each new goal
    // -------------------------------------------------------------------------

    @Test
    void resetBetweenExecutions_abortFlagClearedForNextExecution() {
        when(searchCodeTool.execute(any())).thenReturn(ToolResult.success("found"));

        // First execution: abort after 1 call
        rateLimiter.reset();
        provider.resetAbort();
        provider.searchWorkspace("query1");
        provider.abort();

        // Verify abort is active
        assertThat(provider.searchWorkspace("query2")).contains("maximum tool calls");

        // Second execution: reset clears abort
        rateLimiter.reset();
        provider.resetAbort();

        String result = provider.searchWorkspace("query3");
        assertThat(result).isEqualTo("found"); // not an abort message
    }

    @Test
    void rateLimiterReset_countersStartFreshEachExecution() {
        when(searchCodeTool.execute(any())).thenReturn(ToolResult.success("result"));

        // First execution: exhaust CODE_SEARCH rate limit
        rateLimiter.reset();
        provider.resetAbort();
        for (int i = 0; i < ToolRateLimiter.DEFAULT_LIMIT; i++) {
            provider.searchWorkspace("query" + i);
        }
        // One more should hit the limit
        String limitHit = provider.searchWorkspace("over-limit");
        assertThat(limitHit).startsWith("ERROR:").contains("rate limit");

        // Second execution: reset clears counters
        rateLimiter.reset();
        provider.resetAbort();
        String afterReset = provider.searchWorkspace("fresh-start");
        assertThat(afterReset).isEqualTo("result"); // succeeds again
    }

    // -------------------------------------------------------------------------
    // Loop termination — MAX_TOOL_CALLS_PER_EXECUTION is correctly sized
    // -------------------------------------------------------------------------

    @Test
    void maxToolCallsConstant_isReasonableForAgentTasks() {
        // 30 tool calls is the design limit — enough for complex tasks,
        // small enough to prevent runaway agents.
        assertThat(FunctionCallingAgentService.MAX_TOOL_CALLS_PER_EXECUTION)
                .isGreaterThanOrEqualTo(20)
                .isLessThanOrEqualTo(50);
    }
}
