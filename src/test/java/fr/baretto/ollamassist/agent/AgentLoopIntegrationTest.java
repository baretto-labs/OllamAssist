package fr.baretto.ollamassist.agent;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import fr.baretto.ollamassist.agent.critic.CriticAgent;
import fr.baretto.ollamassist.agent.critic.CriticDecision;
import fr.baretto.ollamassist.agent.plan.AgentPlan;
import fr.baretto.ollamassist.agent.plan.Phase;
import fr.baretto.ollamassist.agent.plan.Step;
import fr.baretto.ollamassist.agent.tools.ToolDispatcher;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the full Plan → Execute → Critic agent loop.
 *
 * Uses mock tools and a mock Critic to verify the orchestrator's behavior
 * under realistic multi-phase scenarios.
 */
class AgentLoopIntegrationTest {

    private AgentOrchestrator orchestrator;
    private AgentProgressNotifier mockNotifier;
    private ToolDispatcher mockDispatcher;
    private CriticAgent mockCritic;

    @BeforeEach
    void setUp() {
        Project mockProject = mock(Project.class);
        MessageBus mockBus = mock(MessageBus.class);
        mockNotifier = mock(AgentProgressNotifier.class);
        MessageBusConnection mockConnection = mock(MessageBusConnection.class);

        when(mockProject.getMessageBus()).thenReturn(mockBus);
        when(mockBus.connect(any(AgentOrchestrator.class))).thenReturn(mockConnection);
        when(mockBus.syncPublisher(AgentProgressNotifier.TOPIC)).thenReturn(mockNotifier);
        when(mockProject.getService(AgentMemoryService.class)).thenReturn(null); // no memory in tests

        orchestrator = new AgentOrchestrator(mockProject);
        mockDispatcher = mock(ToolDispatcher.class);
        mockCritic = mock(CriticAgent.class);
    }

    // -------------------------------------------------------------------------
    // Scenario 1: FILE_FIND → FILE_READ → FILE_EDIT (typical refactoring task)
    // -------------------------------------------------------------------------

    @Test
    void fullLoop_findReadEdit_allPhasesSucceed_completedEvent() throws Exception {
        Step findStep = new Step("FILE_FIND", "Find Foo.java", Map.of("pattern", "**/Foo.java"));
        Step readStep = new Step("FILE_READ", "Read Foo.java", Map.of("path", "{{prev_output_first_line}}"));
        Step editStep = new Step("FILE_EDIT", "Apply edit", Map.of(
                "path", "{{prev_output_first_line}}", "search", "old", "replace", "new"));

        Phase phase1 = new Phase("Locate file", List.of(findStep));
        Phase phase2 = new Phase("Read file", List.of(readStep));
        Phase phase3 = new Phase("Edit file", List.of(editStep));
        AgentPlan plan = new AgentPlan("Refactor Foo", "locate then edit", List.of(phase1, phase2, phase3));

        when(mockDispatcher.dispatch(any(), any(), any()))
                .thenReturn(ToolResult.success("src/main/Foo.java"))   // FILE_FIND
                .thenReturn(ToolResult.success("public class Foo {...}")) // FILE_READ
                .thenReturn(ToolResult.success("File edited: src/main/Foo.java")); // FILE_EDIT
        when(mockCritic.evaluate(any())).thenReturn(ok());

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        assertEventPublished(AgentProgressEvent.Type.COMPLETED);
        verify(mockCritic, times(3)).evaluate(any());
    }

    @Test
    void fullLoop_criticReceivesExecutionHistoryAfterSecondPhase() throws Exception {
        Step s1 = new Step("GIT_STATUS", "Check status", Map.of());
        Step s2 = new Step("GIT_DIFF", "Get diff", Map.of());
        Phase p1 = new Phase("Read git state", List.of(s1));
        Phase p2 = new Phase("Analyze diff", List.of(s2));
        AgentPlan plan = new AgentPlan("Review changes", "check git", List.of(p1, p2));

        when(mockDispatcher.dispatch(any(), any(), any()))
                .thenReturn(ToolResult.success("nothing to commit"))
                .thenReturn(ToolResult.success("diff output"));
        when(mockCritic.evaluate(any())).thenReturn(ok());

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockCritic, times(2)).evaluate(promptCaptor.capture());

        // Second Critic call should include history from phase 1
        String secondPrompt = promptCaptor.getAllValues().get(1);
        assertThat(secondPrompt).contains("Read git state");
        assertThat(secondPrompt).contains("nothing to commit");
    }

    // -------------------------------------------------------------------------
    // Scenario 2: Step failure → Critic adapts → new phase executes
    // -------------------------------------------------------------------------

    @Test
    void stepFails_criticAdapts_revisedPhaseExecutes_completedEvent() throws Exception {
        Step failingStep = new Step("FILE_EDIT", "Edit missing file", Map.of(
                "path", "missing.java", "search", "old", "replace", "new"));
        Step findStep = new Step("FILE_FIND", "Find actual file", Map.of("pattern", "**/*.java"));
        Phase phase1 = new Phase("Edit (will fail)", List.of(failingStep));
        Phase revisedPhase = new Phase("Find then edit", List.of(findStep));
        AgentPlan plan = new AgentPlan("Fix file", "edit directly", List.of(phase1));

        when(mockDispatcher.dispatch(any(), any(), any()))
                .thenReturn(ToolResult.failure("File not found: missing.java"))
                .thenReturn(ToolResult.success("src/main/Real.java"));

        when(mockCritic.evaluate(any()))
                .thenReturn(new CriticDecision(CriticDecision.Status.ADAPT,
                        "File not found — use FILE_FIND first", List.of(revisedPhase)))
                .thenReturn(ok());

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        assertEventPublished(AgentProgressEvent.Type.PLAN_ADAPTED);
        assertEventPublished(AgentProgressEvent.Type.COMPLETED);
    }

    // -------------------------------------------------------------------------
    // Scenario 3: Critic ABORT after step failure
    // -------------------------------------------------------------------------

    @Test
    void stepFails_criticAborts_abortedEventWithReason() throws Exception {
        Step badStep = new Step("RUN_COMMAND", "Run build", Map.of("command", "./gradlew build"));
        Phase phase = new Phase("Build", List.of(badStep));
        AgentPlan plan = new AgentPlan("Build project", "run gradle", List.of(phase));

        when(mockDispatcher.dispatch(any(), any(), any())).thenReturn(ToolResult.failure("Build tool not found"));
        when(mockCritic.evaluate(any())).thenReturn(
                new CriticDecision(CriticDecision.Status.ABORT, "Build environment is not available", List.of()));

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());

        List<AgentProgressEvent> abortedEvents = captor.getAllValues().stream()
                .filter(e -> e.getType() == AgentProgressEvent.Type.ABORTED)
                .toList();
        assertThat(abortedEvents).hasSize(1);
        assertThat(abortedEvents.get(0).getMessage()).contains("Build environment is not available");
    }

    // -------------------------------------------------------------------------
    // Scenario 4: Malformed Critic response → ABORTED with explanation
    // -------------------------------------------------------------------------

    @Test
    void criticThrowsException_abortedEventPublished() throws Exception {
        Step step = new Step("FILE_READ", "Read file", Map.of("path", "foo.java"));
        Phase phase = new Phase("Read", List.of(step));
        AgentPlan plan = new AgentPlan("goal", "r", List.of(phase));

        when(mockDispatcher.dispatch(any(), any(), any())).thenReturn(ToolResult.success("content"));
        when(mockCritic.evaluate(any())).thenThrow(new RuntimeException("JSON deserialization failed: CONTINUE is not a valid Status"));

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentProgressEvent::getType)
                .contains(AgentProgressEvent.Type.ABORTED);
        assertThat(captor.getAllValues().stream()
                .filter(e -> e.getType() == AgentProgressEvent.Type.ABORTED)
                .map(AgentProgressEvent::getMessage)
                .findFirst().orElse(""))
                .contains("malformed");
    }

    // -------------------------------------------------------------------------
    // Scenario 5: Cancellation mid-execution
    // -------------------------------------------------------------------------

    @Test
    void cancelExecution_abortsBetweenPhases() throws Exception {
        Step s = new Step("GIT_STATUS", "Check", Map.of());
        Phase p1 = new Phase("Phase 1", List.of(s));
        Phase p2 = new Phase("Phase 2", List.of(s));
        AgentPlan plan = new AgentPlan("goal", "r", List.of(p1, p2));

        when(mockDispatcher.dispatch(any(), any(), any())).thenAnswer(inv -> {
            orchestrator.cancelExecution(); // cancel during first step
            return ToolResult.success("ok");
        });
        when(mockCritic.evaluate(any())).thenReturn(ok());

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        assertEventPublished(AgentProgressEvent.Type.ABORTED);
        // Phase 2 should never start
        verify(mockDispatcher, times(1)).dispatch(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Scenario 6: Too many critic adaptations → aborted
    // -------------------------------------------------------------------------

    @Test
    void tooManyCriticAdaptations_abortedWithLoopMessage() throws Exception {
        Step s = new Step("FILE_READ", "Read", Map.of("path", "foo.java"));
        Phase loopPhase = new Phase("Loop phase", List.of(s));
        AgentPlan plan = new AgentPlan("goal", "r", List.of(loopPhase));

        when(mockDispatcher.dispatch(any(), any(), any())).thenReturn(ToolResult.success("ok"));
        // Always return ADAPT with a new phase — will trigger infinite loop guard
        when(mockCritic.evaluate(any())).thenReturn(
                new CriticDecision(CriticDecision.Status.ADAPT, "keep adapting", List.of(loopPhase)));

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        assertThat(captor.getAllValues().stream()
                .filter(e -> e.getType() == AgentProgressEvent.Type.ABORTED)
                .map(AgentProgressEvent::getMessage)
                .findFirst().orElse(""))
                .containsIgnoringCase("adaptation");
    }

    // -------------------------------------------------------------------------
    // BUG-1: Critic ADAPT with invalid revisedPhases must be rejected
    // -------------------------------------------------------------------------

    @Test
    void criticAdapts_withUnknownToolId_isRejected() throws Exception {
        Step s = new Step("FILE_READ", "Read", Map.of("path", "foo.java"));
        Phase p = new Phase("Read phase", List.of(s));
        AgentPlan plan = new AgentPlan("goal", "r", List.of(p));

        // Critic returns ADAPT with a hallucinated tool ID
        Step hallucinatedStep = new Step("HALLUCINATED_TOOL", "do something", Map.of());
        Phase badPhase = new Phase("Bad phase", List.of(hallucinatedStep));

        when(mockDispatcher.dispatch(any(), any(), any())).thenReturn(ToolResult.success("content"));
        when(mockCritic.evaluate(any())).thenReturn(
                new CriticDecision(CriticDecision.Status.ADAPT, "try this instead", List.of(badPhase)));

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentProgressEvent::getType)
                .contains(AgentProgressEvent.Type.ABORTED);
        assertThat(captor.getAllValues().stream()
                .filter(e -> e.getType() == AgentProgressEvent.Type.ABORTED)
                .map(AgentProgressEvent::getMessage)
                .findFirst().orElse(""))
                .containsIgnoringCase("invalid revised phases");
    }

    @Test
    void criticAdapts_withTooManyDeleteSteps_isRejected() throws Exception {
        Step s = new Step("FILE_READ", "Read", Map.of("path", "foo.java"));
        Phase p = new Phase("Read phase", List.of(s));
        AgentPlan plan = new AgentPlan("goal", "r", List.of(p));

        // Critic returns ADAPT with 4 FILE_DELETE steps — exceeds MAX_DELETE_STEPS=3
        Step deleteStep = new Step("FILE_DELETE", "delete file", Map.of("path", "foo.java"));
        Phase massDeletePhase = new Phase("Mass delete", List.of(deleteStep, deleteStep, deleteStep, deleteStep));

        when(mockDispatcher.dispatch(any(), any(), any())).thenReturn(ToolResult.success("content"));
        when(mockCritic.evaluate(any())).thenReturn(
                new CriticDecision(CriticDecision.Status.ADAPT, "clean up", List.of(massDeletePhase)));

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        assertEventPublished(AgentProgressEvent.Type.ABORTED);
    }

    // -------------------------------------------------------------------------
    // TECH-1: Critic prompt context truncation
    // -------------------------------------------------------------------------

    @Test
    void criticPrompt_longStepOutput_isTruncated() throws Exception {
        // PromptSanitizer caps each step output to 2000 chars. To push phaseResults
        // above MAX_CRITIC_CONTEXT_CHARS (3000), we need at least 2 steps with large output.
        Step s1 = new Step("FILE_READ", "Read file 1", Map.of("path", "a.java"));
        Step s2 = new Step("FILE_READ", "Read file 2", Map.of("path", "b.java"));
        Phase p = new Phase("Read phase", List.of(s1, s2));
        AgentPlan plan = new AgentPlan("goal", "r", List.of(p));

        // Each tool call returns a 3000-char output; sanitizer caps at 2000 each → combined ~4000+ chars
        String hugeOutput = "x".repeat(3_000);
        when(mockDispatcher.dispatch(any(), any(), any())).thenReturn(ToolResult.success(hugeOutput));
        when(mockCritic.evaluate(any())).thenReturn(ok());

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockCritic).evaluate(promptCaptor.capture());
        String criticPrompt = promptCaptor.getValue();

        assertThat(criticPrompt).contains("context truncated");
    }

    // -------------------------------------------------------------------------
    // UX-2: stepCompleted event carries tool output
    // -------------------------------------------------------------------------

    @Test
    void stepCompleted_event_carriesToolOutput() throws Exception {
        Step s = new Step("GIT_STATUS", "Check git status", Map.of());
        Phase p = new Phase("Git phase", List.of(s));
        AgentPlan plan = new AgentPlan("goal", "r", List.of(p));

        when(mockDispatcher.dispatch(any(), any(), any())).thenReturn(ToolResult.success("nothing to commit"));
        when(mockCritic.evaluate(any())).thenReturn(ok());

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        AgentProgressEvent completedEvent = captor.getAllValues().stream()
                .filter(e -> e.getType() == AgentProgressEvent.Type.STEP_COMPLETED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No STEP_COMPLETED event found"));

        assertThat(completedEvent.getOutput()).isEqualTo("nothing to commit");
    }

    // -------------------------------------------------------------------------
    // UX-5: Retry / Skip on failed step
    // -------------------------------------------------------------------------

    @Test
    void stepFails_retryDecision_stepExecutedTwice_thenSucceeds() throws Exception {
        Step s = new Step("FILE_READ", "Read file", Map.of("path", "foo.java"));
        Phase p = new Phase("Read phase", List.of(s));
        AgentPlan plan = new AgentPlan("goal", "r", List.of(p));

        // First dispatch fails, second succeeds (simulating a transient error)
        when(mockDispatcher.dispatch(any(), any(), any()))
                .thenReturn(ToolResult.failure("file locked"))
                .thenReturn(ToolResult.success("file content"));
        when(mockCritic.evaluate(any())).thenReturn(ok());

        // RETRY decision: re-dispatch the same step
        orchestrator.execute(plan, mockDispatcher, mockCritic,
                (step, err) -> StepRetryDecision.RETRY).get();

        // The step was dispatched twice (original attempt + retry)
        verify(mockDispatcher, times(2)).dispatch(any(), any(), any());
        assertEventPublished(AgentProgressEvent.Type.COMPLETED);
    }

    @Test
    void stepFails_skipDecision_nextStepExecutes_phaseNotFailed() throws Exception {
        Step failingStep = new Step("CODE_SEARCH", "Search code", Map.of("query", "foo"));
        Step nextStep = new Step("GIT_STATUS", "Check status", Map.of());
        Phase p = new Phase("Phase", List.of(failingStep, nextStep));
        AgentPlan plan = new AgentPlan("goal", "r", List.of(p));

        when(mockDispatcher.dispatch(any(), any(), any()))
                .thenReturn(ToolResult.failure("search engine unavailable"))
                .thenReturn(ToolResult.success("nothing to commit"));
        when(mockCritic.evaluate(any())).thenReturn(ok());

        // SKIP decision: continue to next step without marking phase as failed
        orchestrator.execute(plan, mockDispatcher, mockCritic,
                (step, err) -> StepRetryDecision.SKIP).get();

        // Both steps were dispatched
        verify(mockDispatcher, times(2)).dispatch(any(), any(), any());
        assertEventPublished(AgentProgressEvent.Type.COMPLETED);

        // Critic should have received a non-failure phase prompt (phaseFailed=false)
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockCritic).evaluate(promptCaptor.capture());
        // The FAILED entry appears in phase results but the phase status is "completed"
        assertThat(promptCaptor.getValue()).doesNotContain("FAILED (one or more steps failed)");
    }

    @Test
    void stepFails_abortPhaseDecision_criticRunsWithPhaseFailed() throws Exception {
        Step s = new Step("FILE_EDIT", "Edit file", Map.of("path", "x.java"));
        Phase p = new Phase("Edit phase", List.of(s));
        AgentPlan plan = new AgentPlan("goal", "r", List.of(p));

        when(mockDispatcher.dispatch(any(), any(), any())).thenReturn(ToolResult.failure("no such file"));
        when(mockCritic.evaluate(any())).thenReturn(
                new CriticDecision(CriticDecision.Status.ABORT, "cannot continue", List.of()));

        // ABORT_PHASE decision: same as legacy — stop phase, let critic decide
        orchestrator.execute(plan, mockDispatcher, mockCritic,
                (step, err) -> StepRetryDecision.ABORT_PHASE).get();

        // Critic receives a failed-phase prompt (phaseFailed=true adds the recovery hint)
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockCritic).evaluate(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("One or more steps in this phase failed");
        assertEventPublished(AgentProgressEvent.Type.ABORTED);
    }

    @Test
    void stepFails_retryThenSkip_executionContinues() throws Exception {
        Step failingStep = new Step("FILE_FIND", "Find file", Map.of("pattern", "**/*.java"));
        Phase p = new Phase("Find phase", List.of(failingStep));
        AgentPlan plan = new AgentPlan("goal", "r", List.of(p));

        // All dispatches fail
        when(mockDispatcher.dispatch(any(), any(), any())).thenReturn(ToolResult.failure("not found"));
        when(mockCritic.evaluate(any())).thenReturn(ok());

        int[] callCount = {0};
        orchestrator.execute(plan, mockDispatcher, mockCritic,
                (step, err) -> {
                    callCount[0]++;
                    return callCount[0] == 1 ? StepRetryDecision.RETRY : StepRetryDecision.SKIP;
                }).get();

        // Step dispatched 2 times: original + 1 retry, then skipped
        verify(mockDispatcher, times(2)).dispatch(any(), any(), any());
        assertEventPublished(AgentProgressEvent.Type.COMPLETED);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void assertEventPublished(AgentProgressEvent.Type type) {
        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentProgressEvent::getType)
                .contains(type);
    }

    private CriticDecision ok() {
        return new CriticDecision(CriticDecision.Status.OK, "looks good", List.of());
    }
}
