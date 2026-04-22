package fr.baretto.ollamassist.agent;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import fr.baretto.ollamassist.agent.plan.AgentPlan;
import fr.baretto.ollamassist.agent.plan.Phase;
import fr.baretto.ollamassist.agent.plan.Step;
import fr.baretto.ollamassist.events.ChatModelModifiedNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AgentOrchestratorTest {

    private Project mockProject;
    private MessageBus mockBus;
    private AgentProgressNotifier mockNotifier;
    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        mockProject = mock(Project.class);
        mockBus = mock(MessageBus.class);
        mockNotifier = mock(AgentProgressNotifier.class);
        MessageBusConnection mockConnection = mock(MessageBusConnection.class);

        when(mockProject.getMessageBus()).thenReturn(mockBus);
        when(mockBus.connect(any(AgentOrchestrator.class))).thenReturn(mockConnection);
        when(mockBus.syncPublisher(AgentProgressNotifier.TOPIC)).thenReturn(mockNotifier);

        orchestrator = new AgentOrchestrator(mockProject);
    }

    private AgentPlan validPlan() {
        Step step = new Step("FILE_READ", "Read build.gradle", null);
        Phase phase = new Phase("Explore project", List.of(step));
        return new AgentPlan("Understand the build", "Start by reading build config", List.of(phase));
    }

    // --- plan() returns the plan from PlannerAgent ---

    @Test
    void plan_returnsPlanFromAgent() throws Exception {
        AgentPlan expected = validPlan();
        PlannerAgent mockAgent = mock(PlannerAgent.class);
        when(mockAgent.plan(any())).thenReturn(expected);

        AgentPlan result = orchestrator.plan("Understand the build", mockAgent).get();

        assertThat(result).isSameAs(expected);
    }

    @Test
    void plan_passesGoalToAgent() throws Exception {
        PlannerAgent mockAgent = mock(PlannerAgent.class);
        when(mockAgent.plan(any())).thenReturn(validPlan());

        orchestrator.plan("Fix the NPE in Foo.java", mockAgent).get();

        verify(mockAgent).plan("Fix the NPE in Foo.java");
    }

    // --- Progress events are published in the right order ---

    @Test
    void plan_publishesPlanningThenPlanReady() throws Exception {
        PlannerAgent mockAgent = mock(PlannerAgent.class);
        when(mockAgent.plan(any())).thenReturn(validPlan());
        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);

        orchestrator.plan("goal", mockAgent).get();

        verify(mockNotifier, times(2)).onProgress(captor.capture());
        List<AgentProgressEvent> events = captor.getAllValues();
        assertThat(events.get(0).getType()).isEqualTo(AgentProgressEvent.Type.PLANNING);
        assertThat(events.get(1).getType()).isEqualTo(AgentProgressEvent.Type.PLAN_READY);
    }

    @Test
    void plan_planReadyEvent_carriesThePlan() throws Exception {
        AgentPlan expected = validPlan();
        PlannerAgent mockAgent = mock(PlannerAgent.class);
        when(mockAgent.plan(any())).thenReturn(expected);
        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);

        orchestrator.plan("goal", mockAgent).get();

        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        AgentProgressEvent planReadyEvent = captor.getAllValues().stream()
                .filter(e -> e.getType() == AgentProgressEvent.Type.PLAN_READY)
                .findFirst()
                .orElseThrow();
        assertThat(planReadyEvent.getPlan()).isSameAs(expected);
    }

    // --- Validation ---

    @Test
    void plan_emptyPlan_publishesAbortedAndThrows() {
        AgentPlan emptyPlan = new AgentPlan("goal", "reasoning", List.of());
        PlannerAgent mockAgent = mock(PlannerAgent.class);
        when(mockAgent.plan(any())).thenReturn(emptyPlan);

        assertThatThrownBy(() -> orchestrator.plan("goal", mockAgent).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentProgressEvent::getType)
                .contains(AgentProgressEvent.Type.ABORTED);
    }

    @Test
    void plan_nullPlan_publishesAbortedAndThrows() {
        PlannerAgent mockAgent = mock(PlannerAgent.class);
        when(mockAgent.plan(any())).thenReturn(null);

        assertThatThrownBy(() -> orchestrator.plan("goal", mockAgent).get())
                .isInstanceOf(ExecutionException.class);

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentProgressEvent::getType)
                .contains(AgentProgressEvent.Type.ABORTED);
    }

    @Test
    void plan_agentThrows_publishesAbortedAndPropagates() {
        PlannerAgent mockAgent = mock(PlannerAgent.class);
        when(mockAgent.plan(any())).thenThrow(new RuntimeException("Ollama timeout"));

        assertThatThrownBy(() -> orchestrator.plan("goal", mockAgent).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Plan generation failed");

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentProgressEvent::getType)
                .contains(AgentProgressEvent.Type.ABORTED);
    }

    // --- G3 blast-radius guard: too many FILE_DELETE steps ---

    @Test
    void plan_tooManyDeleteSteps_publishesAbortedAndThrows() {
        Step deleteStep = new Step("FILE_DELETE", "delete file", null);
        Phase phase = new Phase("Delete everything", List.of(deleteStep, deleteStep, deleteStep, deleteStep));
        AgentPlan dangerousPlan = new AgentPlan("Delete all logs", "reasoning", List.of(phase));
        PlannerAgent mockAgent = mock(PlannerAgent.class);
        when(mockAgent.plan(any())).thenReturn(dangerousPlan);

        assertThatThrownBy(() -> orchestrator.plan("Delete all logs", mockAgent).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentProgressEvent::getType)
                .contains(AgentProgressEvent.Type.ABORTED);
    }

    @Test
    void plan_exactlyMaxDeleteSteps_isAccepted() throws Exception {
        // MAX_DELETE_STEPS is 3 — a plan with exactly 3 FILE_DELETE steps must pass validation
        Step deleteStep = new Step("FILE_DELETE", "delete file", null);
        Phase phase = new Phase("Cleanup", List.of(deleteStep, deleteStep, deleteStep));
        AgentPlan plan = new AgentPlan("Cleanup temp files", "reasoning", List.of(phase));
        PlannerAgent mockAgent = mock(PlannerAgent.class);
        when(mockAgent.plan(any())).thenReturn(plan);

        AgentPlan result = orchestrator.plan("Cleanup temp files", mockAgent).get();

        assertThat(result).isSameAs(plan);
    }

    // --- Phase/step count limits (P1.2) ---

    @Test
    void plan_tooManyPhases_publishesAbortedAndThrows() {
        // MAX_PHASES is 10 — a plan with 11 phases must be rejected
        Step step = new Step("FILE_READ", "read", null);
        List<Phase> phases = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            phases.add(new Phase("Phase " + i, List.of(step)));
        }
        AgentPlan plan = new AgentPlan("goal", "reasoning", phases);
        PlannerAgent mockAgent = mock(PlannerAgent.class);
        when(mockAgent.plan(any())).thenReturn(plan);

        assertThatThrownBy(() -> orchestrator.plan("goal", mockAgent).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentProgressEvent::getType)
                .contains(AgentProgressEvent.Type.ABORTED);
    }

    @Test
    void plan_exactlyMaxPhases_isAccepted() throws Exception {
        Step step = new Step("FILE_READ", "read", null);
        List<Phase> phases = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            phases.add(new Phase("Phase " + i, List.of(step)));
        }
        AgentPlan plan = new AgentPlan("goal", "reasoning", phases);
        PlannerAgent mockAgent = mock(PlannerAgent.class);
        when(mockAgent.plan(any())).thenReturn(plan);

        AgentPlan result = orchestrator.plan("goal", mockAgent).get();

        assertThat(result).isSameAs(plan);
    }

    @Test
    void plan_tooManyTotalSteps_publishesAbortedAndThrows() {
        // MAX_TOTAL_STEPS is 30 — 3 phases × 11 steps = 33, must be rejected
        List<Step> steps = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            steps.add(new Step("FILE_READ", "read " + i, null));
        }
        List<Phase> phases = List.of(
                new Phase("Phase 1", steps),
                new Phase("Phase 2", steps),
                new Phase("Phase 3", steps)
        );
        AgentPlan plan = new AgentPlan("goal", "reasoning", phases);
        PlannerAgent mockAgent = mock(PlannerAgent.class);
        when(mockAgent.plan(any())).thenReturn(plan);

        assertThatThrownBy(() -> orchestrator.plan("goal", mockAgent).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentProgressEvent::getType)
                .contains(AgentProgressEvent.Type.ABORTED);
    }

    @Test
    void plan_exactlyMaxTotalSteps_isAccepted() throws Exception {
        // 3 phases × 10 steps = 30 — exactly at the limit
        List<Step> steps = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            steps.add(new Step("FILE_READ", "read " + i, null));
        }
        List<Phase> phases = List.of(
                new Phase("Phase 1", steps),
                new Phase("Phase 2", steps),
                new Phase("Phase 3", steps)
        );
        AgentPlan plan = new AgentPlan("goal", "reasoning", phases);
        PlannerAgent mockAgent = mock(PlannerAgent.class);
        when(mockAgent.plan(any())).thenReturn(plan);

        AgentPlan result = orchestrator.plan("goal", mockAgent).get();

        assertThat(result).isSameAs(plan);
    }

    // --- Unknown toolId validation (P0-D) ---

    @Test
    void plan_unknownToolId_publishesAbortedAndThrows() {
        Step badStep = new Step("HALLUCINATED_TOOL", "do something", null);
        Phase phase = new Phase("Phase 1", List.of(badStep));
        AgentPlan planWithBadTool = new AgentPlan("goal", "reasoning", List.of(phase));
        PlannerAgent mockAgent = mock(PlannerAgent.class);
        when(mockAgent.plan(any())).thenReturn(planWithBadTool);

        assertThatThrownBy(() -> orchestrator.plan("goal", mockAgent).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentProgressEvent::getType)
                .contains(AgentProgressEvent.Type.ABORTED);
    }

    @Test
    void plan_mixedKnownAndUnknownToolIds_isRejected() {
        Step goodStep = new Step("FILE_READ", "read file", null);
        Step badStep = new Step("FAKE_TOOL", "fake", null);
        Phase phase = new Phase("Mixed phase", List.of(goodStep, badStep));
        AgentPlan plan = new AgentPlan("goal", "reasoning", List.of(phase));
        PlannerAgent mockAgent = mock(PlannerAgent.class);
        when(mockAgent.plan(any())).thenReturn(plan);

        assertThatThrownBy(() -> orchestrator.plan("goal", mockAgent).get())
                .isInstanceOf(ExecutionException.class);
    }

    // --- Model change invalidation ---

    @Test
    void constructor_subscribesToChatModelModifiedNotifier() {
        verify(mockBus.connect(orchestrator)).subscribe(eq(ChatModelModifiedNotifier.TOPIC), any());
    }

    // -------------------------------------------------------------------------
    // trimExecutionLog — invariant: log.length() <= MAX after trim
    // -------------------------------------------------------------------------

    @Test
    void trimExecutionLog_afterTrim_logDoesNotExceedMax() {
        // Given a log larger than MAX
        StringBuilder log = new StringBuilder("x".repeat(AgentOrchestrator.MAX_EXECUTION_LOG_CHARS + 500));
        AgentOrchestrator.trimExecutionLog(log);
        assertThat(log.length()).isLessThanOrEqualTo(AgentOrchestrator.MAX_EXECUTION_LOG_CHARS);
    }

    @Test
    void trimExecutionLog_markerPresent_afterTrim() {
        StringBuilder log = new StringBuilder("x".repeat(AgentOrchestrator.MAX_EXECUTION_LOG_CHARS + 100));
        AgentOrchestrator.trimExecutionLog(log);
        assertThat(log.toString()).startsWith("[earlier history trimmed]");
    }

    @Test
    void trimExecutionLog_immediateSecondCall_isNoOp() {
        // After a trim the result must be <= MAX so a second immediate call
        // (no new content added) must NOT trim again — the marker must stay the same.
        StringBuilder log = new StringBuilder("x".repeat(AgentOrchestrator.MAX_EXECUTION_LOG_CHARS + 200));
        AgentOrchestrator.trimExecutionLog(log);
        int lengthAfterFirst = log.length();
        String contentAfterFirst = log.toString();

        AgentOrchestrator.trimExecutionLog(log);

        assertThat(log.length()).isEqualTo(lengthAfterFirst);
        assertThat(log.toString()).isEqualTo(contentAfterFirst);
    }

    @Test
    void trimExecutionLog_belowMax_isNoOp() {
        String content = "normal log entry\n";
        StringBuilder log = new StringBuilder(content);
        AgentOrchestrator.trimExecutionLog(log);
        assertThat(log.toString()).isEqualTo(content);
    }
}
