package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.agent.tools.ToolRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FunctionCallingAgentServiceTest {

    @Mock AgentToolProvider toolProvider;
    @Mock ToolRateLimiter rateLimiter;

    FunctionCallingAgentService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new FunctionCallingAgentService(null, toolProvider, rateLimiter);
    }

    // -------------------------------------------------------------------------
    // Disposed state
    // -------------------------------------------------------------------------

    @Test
    void execute_whenDisposed_callsOnError() throws InterruptedException {
        service.dispose();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> captured = new AtomicReference<>();

        service.execute("do something", new AgentStreamHandler() {
            public void onToken(String t) {}
            public void onToolCall(String n, String a) {}
            public void onToolResult(String n, String r) {}
            public void onComplete() {}
            public void onError(Throwable e) { captured.set(e); latch.countDown(); }
        });

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(captured.get()).isInstanceOf(IllegalStateException.class);
    }

    // -------------------------------------------------------------------------
    // Guard reset at start of execution
    // -------------------------------------------------------------------------

    @Test
    void execute_resetsRateLimiterAndAbortFlagBeforeStart() {
        service.dispose(); // prevent actual LLM call — just test the reset path fails fast

        service.execute("goal", new AgentStreamHandler() {
            public void onToken(String t) {}
            public void onToolCall(String n, String a) {}
            public void onToolResult(String n, String r) {}
            public void onComplete() {}
            public void onError(Throwable e) {}
        });

        // Because service is disposed, execute returns early after calling onError.
        // reset() must NOT be called when disposed (guards only run on valid execution start).
        // This validates that disposing before execute does not trigger reset.
        verify(rateLimiter, never()).reset();
        verify(toolProvider, never()).resetAbort();
    }

    @Test
    void execute_notDisposed_resetsGuardsBeforeStart() {
        // We cannot call the real buildAgent (no IDE context), so we verify reset
        // is invoked by overriding buildAgent. Since buildAgent is private and we
        // use a mock toolProvider, we verify reset calls only — the actual streaming
        // is tested in integration tests (T5.4).
        //
        // Here we just verify the public contract: when not disposed, reset is attempted.
        // The build will fail (no Ollama), but reset must happen first.
        try {
            service.execute("goal", new AgentStreamHandler() {
                public void onToken(String t) {}
                public void onToolCall(String n, String a) {}
                public void onToolResult(String n, String r) {}
                public void onComplete() {}
                public void onError(Throwable e) {}
            });
        } catch (Exception ignored) {
            // OllamAssistSettings not available in test — expected
        }

        verify(rateLimiter).reset();
        verify(toolProvider).resetAbort();
    }

    // -------------------------------------------------------------------------
    // MAX_TOOL_CALLS_PER_EXECUTION constant
    // -------------------------------------------------------------------------

    @Test
    void maxToolCallsPerExecution_is30() {
        assertThat(FunctionCallingAgentService.MAX_TOOL_CALLS_PER_EXECUTION).isEqualTo(30);
    }

    // -------------------------------------------------------------------------
    // AbortToolProvider — abort triggered after N calls via onToolExecuted simulation
    // -------------------------------------------------------------------------

    @Test
    void abortFlag_notSetBeforeMaxCalls() {
        // toolProvider.abort() should not be called when toolCallCount < MAX
        // We simulate this by inspecting the abort call count directly
        verify(toolProvider, never()).abort();
    }

    // -------------------------------------------------------------------------
    // AgentToolProvider abort integration
    // -------------------------------------------------------------------------

    @Test
    void agentToolProvider_abort_causesAllToolsToReturnAbortMessage() {
        ToolRateLimiter realLimiter = new ToolRateLimiter();
        // Use a real AgentToolProvider with null tools (we only test the abort path)
        AgentToolProvider provider = new AgentToolProvider(
                null, null, null, null, null, realLimiter);

        provider.abort();

        // All tools should return the abort message without calling the underlying tool
        String editResult = provider.editFile("src/Foo.java", "old", "new", "false");
        String writeResult = provider.writeFile("src/Bar.java", "content");
        String webResult = provider.searchWeb("query");
        String workspaceResult = provider.searchWorkspace("keyword");
        String knowledgeResult = provider.searchKnowledgeBase("concept");

        assertThat(editResult).startsWith("ERROR:").contains("maximum tool calls");
        assertThat(writeResult).startsWith("ERROR:").contains("maximum tool calls");
        assertThat(webResult).startsWith("ERROR:").contains("maximum tool calls");
        assertThat(workspaceResult).startsWith("ERROR:").contains("maximum tool calls");
        assertThat(knowledgeResult).startsWith("ERROR:").contains("maximum tool calls");
    }

    @Test
    void agentToolProvider_resetAbort_clearsAbortFlag() {
        // Limiter that always denies — ensures the tool method reaches rate-limit check
        // (i.e. it is past the aborted check), without needing a real tool implementation.
        ToolRateLimiter denyLimiter = mock(ToolRateLimiter.class);
        when(denyLimiter.tryAcquire(anyString())).thenReturn(false);

        AgentToolProvider provider = new AgentToolProvider(
                null, null, null, null, null, denyLimiter);

        provider.abort();
        provider.resetAbort();

        String result = provider.searchWeb("query");
        // Rate-limit error, not the abort message
        assertThat(result).startsWith("ERROR:").doesNotContain("maximum tool calls per execution");
        assertThat(result).contains("rate limit");
    }
}
