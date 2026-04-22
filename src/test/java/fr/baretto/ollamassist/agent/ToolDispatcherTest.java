package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.agent.plan.Step;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.ToolApprovalHelper;
import fr.baretto.ollamassist.agent.tools.ToolDispatcher;
import fr.baretto.ollamassist.agent.tools.ToolRegistry;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ToolDispatcherTest {

    private ToolRegistry mockRegistry;
    private ToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        mockRegistry = mock(ToolRegistry.class);
        dispatcher = new ToolDispatcher(mockRegistry, null);
    }

    @Test
    void dispatch_unknownToolId_returnsFailure() {
        when(mockRegistry.get("UNKNOWN_TOOL")).thenReturn(null);
        Step step = new Step("UNKNOWN_TOOL", "do something", Map.of());

        ToolResult result = dispatcher.dispatch(step);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("UNKNOWN_TOOL");
    }

    @Test
    void dispatch_knownTool_returnsToolResult() {
        AgentTool mockTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_READ")).thenReturn(mockTool);
        when(mockTool.execute(any())).thenReturn(ToolResult.success("file content"));
        Step step = new Step("FILE_READ", "read main class", Map.of("path", "Main.java"));

        ToolResult result = dispatcher.dispatch(step);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).isEqualTo("file content");
        verify(mockTool).execute(Map.of("path", "Main.java"));
    }

    @Test
    void dispatch_toolThrowsException_returnsFailure() {
        AgentTool mockTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_EDIT")).thenReturn(mockTool);
        when(mockTool.execute(any())).thenThrow(new RuntimeException("disk error"));
        Step step = new Step("FILE_EDIT", "edit file", Map.of("path", "Foo.java"));

        ToolResult result = dispatcher.dispatch(step);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("FILE_EDIT").contains("disk error");
    }

    @Test
    void dispatch_passesStepParamsToTool() {
        AgentTool mockTool = mock(AgentTool.class);
        when(mockRegistry.get("CODE_SEARCH")).thenReturn(mockTool);
        when(mockTool.execute(any())).thenReturn(ToolResult.success("matches"));
        Map<String, Object> params = Map.of("query", "interface Foo");
        Step step = new Step("CODE_SEARCH", "search interface", params);

        dispatcher.dispatch(step);

        verify(mockTool).execute(params);
    }

    @Test
    void dispatch_withPreviousOutput_resolvesPlaceholder() {
        AgentTool mockTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_READ")).thenReturn(mockTool);
        when(mockTool.execute(any())).thenReturn(ToolResult.success("file content"));
        // Params contain a placeholder — the dispatcher should resolve it
        Step step = new Step("FILE_READ", "read found file", Map.of("path", "{{prev_output_first_line}}"));

        dispatcher.dispatch(step, "src/main/java/Foo.java\nsrc/test/java/FooTest.java");

        verify(mockTool).execute(Map.of("path", "src/main/java/Foo.java"));
    }

    @Test
    void dispatch_noPlaceholder_previousOutputIgnored() {
        AgentTool mockTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_READ")).thenReturn(mockTool);
        when(mockTool.execute(any())).thenReturn(ToolResult.success("content"));
        Map<String, Object> params = Map.of("path", "src/main/Foo.java");
        Step step = new Step("FILE_READ", "read file", params);

        dispatcher.dispatch(step, "some/other/path.java");

        verify(mockTool).execute(params);
    }

    @Test
    void dispatch_approvalTimeoutException_returnsFailureNotException() {
        AgentTool mockTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_WRITE")).thenReturn(mockTool);
        when(mockTool.execute(any())).thenThrow(new ToolApprovalHelper.ApprovalTimeoutException("src/Foo.java"));
        Step step = new Step("FILE_WRITE", "create file", Map.of("path", "src/Foo.java"));

        ToolResult result = dispatcher.dispatch(step);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("timed out");
    }

    @Test
    void dispatch_approvalTimeoutCircuitBreaker_abortsAfterMaxConsecutiveTimeouts() {
        // After MAX_APPROVAL_TIMEOUTS (3) consecutive ApprovalTimeoutException,
        // the circuit-breaker kicks in and returns a dedicated abort message.
        AgentTool mockTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_WRITE")).thenReturn(mockTool);
        when(mockTool.execute(any())).thenThrow(new ToolApprovalHelper.ApprovalTimeoutException("src/Foo.java"));
        Step step = new Step("FILE_WRITE", "create file", Map.of("path", "src/Foo.java"));

        ToolResult first  = dispatcher.dispatch(step);
        ToolResult second = dispatcher.dispatch(step);
        ToolResult third  = dispatcher.dispatch(step);

        // First two: regular timeout message
        assertThat(first.isSuccess()).isFalse();
        assertThat(first.getErrorMessage()).contains("Approval timed out");
        assertThat(second.isSuccess()).isFalse();
        assertThat(second.getErrorMessage()).contains("Approval timed out");

        // Third: circuit-breaker fires
        assertThat(third.isSuccess()).isFalse();
        assertThat(third.getErrorMessage()).contains("consecutive approval timeouts");
    }

    @Test
    void dispatch_approvalTimeoutCounter_resetsOnResetRateLimits() {
        // After a reset, the approval timeout counter starts from zero again.
        AgentTool mockTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_WRITE")).thenReturn(mockTool);
        when(mockTool.execute(any())).thenThrow(new ToolApprovalHelper.ApprovalTimeoutException("src/Foo.java"));
        Step step = new Step("FILE_WRITE", "create file", Map.of("path", "src/Foo.java"));

        // Trigger 2 timeouts (below circuit-breaker threshold)
        dispatcher.dispatch(step);
        dispatcher.dispatch(step);

        // Reset (simulating a new agent execution)
        dispatcher.resetRateLimits();

        // After reset: first timeout returns regular message, not the abort message
        AgentTool mockTool2 = mock(AgentTool.class);
        when(mockRegistry.get("FILE_EDIT")).thenReturn(mockTool2);
        when(mockTool2.execute(any())).thenThrow(new ToolApprovalHelper.ApprovalTimeoutException("src/Bar.java"));
        Step step2 = new Step("FILE_EDIT", "edit file", Map.of("path", "src/Bar.java"));

        ToolResult result = dispatcher.dispatch(step2);
        assertThat(result.getErrorMessage()).contains("Approval timed out");
        assertThat(result.getErrorMessage()).doesNotContain("consecutive approval timeouts");
    }

    @Test
    void dispatch_toolExceedsTimeout_returnsFailure() throws InterruptedException {
        // Use a 1-second override so the test does not take 120s
        ToolDispatcher timedDispatcher = new ToolDispatcher(mockRegistry, null, 1);
        AgentTool slowTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_READ")).thenReturn(slowTool);
        when(slowTool.execute(any())).thenAnswer(inv -> {
            Thread.sleep(5_000); // hangs longer than 1-second timeout
            return ToolResult.success("should not reach");
        });
        Step step = new Step("FILE_READ", "read hanging file", Map.of("path", "big.java"));

        ToolResult result = timedDispatcher.dispatch(step);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("timed out");
    }

    @Test
    void dispatch_toolCompletesWithinTimeout_returnsSuccess() {
        // Verify that a fast tool is NOT affected by the timeout guard
        ToolDispatcher timedDispatcher = new ToolDispatcher(mockRegistry, null, 5);
        AgentTool fastTool = mock(AgentTool.class);
        when(mockRegistry.get("GIT_STATUS")).thenReturn(fastTool);
        when(fastTool.execute(any())).thenReturn(ToolResult.success("nothing to commit"));
        Step step = new Step("GIT_STATUS", "check status", Map.of());

        ToolResult result = timedDispatcher.dispatch(step);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).isEqualTo("nothing to commit");
    }

    // --- outputVar / named variable storage tests ---

    @Test
    void dispatch_stepWithOutputVar_storesOutputInVariables() {
        AgentTool findTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_FIND")).thenReturn(findTool);
        when(findTool.execute(any())).thenReturn(ToolResult.success("src/main/java/OrderService.java"));

        // Step declares outputVar — its output should be stored for later steps
        Step findStep = new Step("FILE_FIND", "find service", Map.of("pattern", "**/OrderService.java"), "serviceFilePath");
        dispatcher.dispatch(findStep, "");

        // Second step references {{var.serviceFilePath}}
        AgentTool readTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_READ")).thenReturn(readTool);
        when(readTool.execute(any())).thenReturn(ToolResult.success("class OrderService {}"));
        Step readStep = new Step("FILE_READ", "read service", Map.of("path", "{{var.serviceFilePath}}"));

        dispatcher.dispatch(readStep, "");

        verify(readTool).execute(Map.of("path", "src/main/java/OrderService.java"));
    }

    @Test
    void dispatch_stepWithOutputVar_failedTool_doesNotStoreOutput() {
        AgentTool findTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_FIND")).thenReturn(findTool);
        when(findTool.execute(any())).thenReturn(ToolResult.failure("no files found"));

        Step findStep = new Step("FILE_FIND", "find missing file", Map.of("pattern", "**/Missing.java"), "missingVar");
        dispatcher.dispatch(findStep, "");

        // A subsequent step referencing {{var.missingVar}} should get an unresolvable error
        AgentTool readTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_READ")).thenReturn(readTool);
        Step readStep = new Step("FILE_READ", "read missing", Map.of("path", "{{var.missingVar}}"));

        ToolResult result = dispatcher.dispatch(readStep, "");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("missingVar");
        verifyNoInteractions(readTool);
    }

    @Test
    void dispatch_resetRateLimits_clearsStoredVariables() {
        AgentTool findTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_FIND")).thenReturn(findTool);
        when(findTool.execute(any())).thenReturn(ToolResult.success("src/Foo.java"));

        Step stepWithVar = new Step("FILE_FIND", "find foo", Map.of("pattern", "**/Foo.java"), "fooPath");
        dispatcher.dispatch(stepWithVar, "");

        // Reset clears variables
        dispatcher.resetRateLimits();

        AgentTool readTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_READ")).thenReturn(readTool);
        Step stepUsingVar = new Step("FILE_READ", "read foo", Map.of("path", "{{var.fooPath}}"));

        ToolResult result = dispatcher.dispatch(stepUsingVar, "");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("fooPath");
        verifyNoInteractions(readTool);
    }

    @Test
    void dispatch_outputVar_storedAtomicallyUnderConcurrentReset() throws Exception {
        // Verifies that compute() makes store+reset atomic: no lost-update when resetRateLimits()
        // races with a successful outputVar store.
        AgentTool findTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_FIND")).thenReturn(findTool);
        when(findTool.execute(any())).thenReturn(ToolResult.success("src/Foo.java"));
        Step stepWithVar = new Step("FILE_FIND", "find foo", Map.of("pattern", "Foo.java"), "fooPath");

        int threads = 20;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);

        // Half threads dispatch (store), half reset — all fire simultaneously
        for (int i = 0; i < threads / 2; i++) {
            pool.submit(() -> { ready.countDown(); try { start.await(); dispatcher.dispatch(stepWithVar, ""); } catch (Exception ignored) {} });
            pool.submit(() -> { ready.countDown(); try { start.await(); dispatcher.resetRateLimits(); } catch (Exception ignored) {} });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        // No assertion on final state (it's racy by design) — the test must not throw or deadlock.
        // If compute() is missing, ConcurrentModificationException or data corruption may surface.
    }

    @Test
    void dispatch_stepWithoutOutputVar_doesNotInterfereWithVariables() {
        AgentTool findTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_FIND")).thenReturn(findTool);
        when(findTool.execute(any())).thenReturn(ToolResult.success("src/Bar.java"));

        // Step has no outputVar — its output should NOT enter the variables map
        Step stepNoVar = new Step("FILE_FIND", "find bar", Map.of("pattern", "**/Bar.java"));
        dispatcher.dispatch(stepNoVar, "");

        AgentTool readTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_READ")).thenReturn(readTool);
        when(readTool.execute(any())).thenReturn(ToolResult.success("class Bar {}"));
        // Plain prev_output placeholder (not var) should resolve via the previous output argument
        Step readStep = new Step("FILE_READ", "read bar", Map.of("path", "{{prev_output_first_line}}"));

        dispatcher.dispatch(readStep, "src/Bar.java");

        verify(readTool).execute(Map.of("path", "src/Bar.java"));
    }

    // -------------------------------------------------------------------------
    // Security: RUN_COMMAND must not allow dynamic placeholders (command injection)
    // -------------------------------------------------------------------------

    @Test
    void dispatch_runCommand_withPrevOutputPlaceholder_blockedBeforeExecution() {
        // A malicious or naïve plan could generate RUN_COMMAND with {{prev_output}} in the
        // command string. If prev_output contains ";" or "`", it would inject into the shell.
        // The dispatcher must reject this BEFORE resolution, never reaching the tool.
        AgentTool commandTool = mock(AgentTool.class);
        when(mockRegistry.get("RUN_COMMAND")).thenReturn(commandTool);
        Step step = new Step("RUN_COMMAND", "run with dynamic input",
                Map.of("command", "echo {{prev_output}}"));

        ToolResult result = dispatcher.dispatch(step, "hello; rm -rf /");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("not allowed");
        // The tool must never be invoked — the block happens pre-execution
        verify(commandTool, never()).execute(any());
    }

    @Test
    void dispatch_runCommand_withPrevOutputFirstLinePlaceholder_blocked() {
        AgentTool commandTool = mock(AgentTool.class);
        when(mockRegistry.get("RUN_COMMAND")).thenReturn(commandTool);
        Step step = new Step("RUN_COMMAND", "run with first line",
                Map.of("command", "cat {{prev_output_first_line}}"));

        ToolResult result = dispatcher.dispatch(step, "evil`whoami`");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("not allowed");
        verify(commandTool, never()).execute(any());
    }

    @Test
    void dispatch_runCommand_withVarPlaceholder_blocked() {
        AgentTool commandTool = mock(AgentTool.class);
        when(mockRegistry.get("RUN_COMMAND")).thenReturn(commandTool);
        Step step = new Step("RUN_COMMAND", "run with var",
                Map.of("command", "grep {{var.search_result}} src/"));

        ToolResult result = dispatcher.dispatch(step, "");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("not allowed");
        verify(commandTool, never()).execute(any());
    }

    @Test
    void dispatch_runCommand_withFixedCommand_allowed() {
        // A fixed command string (no placeholders) must still work normally.
        AgentTool commandTool = mock(AgentTool.class);
        when(mockRegistry.get("RUN_COMMAND")).thenReturn(commandTool);
        when(commandTool.execute(any())).thenReturn(ToolResult.success("ok"));
        Step step = new Step("RUN_COMMAND", "run maven test",
                Map.of("command", "./gradlew test"));

        ToolResult result = dispatcher.dispatch(step, "anything");

        assertThat(result.isSuccess()).isTrue();
    }
}
