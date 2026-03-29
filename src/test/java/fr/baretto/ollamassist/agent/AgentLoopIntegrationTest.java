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

        when(mockDispatcher.dispatch(any(), any()))
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

        when(mockDispatcher.dispatch(any(), any()))
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

        when(mockDispatcher.dispatch(any(), any()))
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

        when(mockDispatcher.dispatch(any(), any())).thenReturn(ToolResult.failure("Build tool not found"));
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

        when(mockDispatcher.dispatch(any(), any())).thenReturn(ToolResult.success("content"));
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

        when(mockDispatcher.dispatch(any(), any())).thenAnswer(inv -> {
            orchestrator.cancelExecution(); // cancel during first step
            return ToolResult.success("ok");
        });
        when(mockCritic.evaluate(any())).thenReturn(ok());

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        assertEventPublished(AgentProgressEvent.Type.ABORTED);
        // Phase 2 should never start
        verify(mockDispatcher, times(1)).dispatch(any(), any());
    }

    // -------------------------------------------------------------------------
    // Scenario 6: Too many critic adaptations → aborted
    // -------------------------------------------------------------------------

    @Test
    void tooManyCriticAdaptations_abortedWithLoopMessage() throws Exception {
        Step s = new Step("FILE_READ", "Read", Map.of("path", "foo.java"));
        Phase loopPhase = new Phase("Loop phase", List.of(s));
        AgentPlan plan = new AgentPlan("goal", "r", List.of(loopPhase));

        when(mockDispatcher.dispatch(any(), any())).thenReturn(ToolResult.success("ok"));
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
