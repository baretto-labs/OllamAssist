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
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentExecuteTest {

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

        orchestrator = new AgentOrchestrator(mockProject);
        mockDispatcher = mock(ToolDispatcher.class);
        mockCritic = mock(CriticAgent.class);
    }

    private AgentPlan singlePhasePlan(String... toolIds) {
        List<Step> steps = java.util.Arrays.stream(toolIds)
                .map(id -> new Step(id, "step " + id, java.util.Map.of()))
                .toList();
        Phase phase = new Phase("Do work", steps);
        return new AgentPlan("Test goal", "reasoning", List.of(phase));
    }

    private CriticDecision ok() {
        return new CriticDecision(CriticDecision.Status.OK, "looks good", List.of());
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void execute_allStepsSucceed_publishesCompletedEvent() throws Exception {
        AgentPlan plan = singlePhasePlan("FILE_READ");
        when(mockDispatcher.dispatch(any(), any())).thenReturn(ToolResult.success("content"));
        when(mockCritic.evaluate(any())).thenReturn(ok());

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentProgressEvent::getType)
                .contains(AgentProgressEvent.Type.COMPLETED);
    }

    @Test
    void execute_publishesStepStartedAndCompleted() throws Exception {
        AgentPlan plan = singlePhasePlan("GIT_STATUS");
        when(mockDispatcher.dispatch(any(), any())).thenReturn(ToolResult.success("clean"));
        when(mockCritic.evaluate(any())).thenReturn(ok());

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        List<AgentProgressEvent.Type> types = captor.getAllValues().stream()
                .map(AgentProgressEvent::getType)
                .toList();
        assertThat(types).contains(AgentProgressEvent.Type.STEP_STARTED, AgentProgressEvent.Type.STEP_COMPLETED);
    }

    @Test
    void execute_publishesCriticThinkingAfterPhase() throws Exception {
        AgentPlan plan = singlePhasePlan("FILE_READ");
        when(mockDispatcher.dispatch(any(), any())).thenReturn(ToolResult.success("ok"));
        when(mockCritic.evaluate(any())).thenReturn(ok());

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentProgressEvent::getType)
                .contains(AgentProgressEvent.Type.CRITIC_THINKING);
    }

    // -------------------------------------------------------------------------
    // Step failure
    // -------------------------------------------------------------------------

    @Test
    void execute_stepFails_publishesStepFailedThenConsultsCritic() throws Exception {
        AgentPlan plan = singlePhasePlan("FILE_EDIT");
        when(mockDispatcher.dispatch(any(), any())).thenReturn(ToolResult.failure("file not found"));
        when(mockCritic.evaluate(any())).thenReturn(
                new CriticDecision(CriticDecision.Status.ABORT, "cannot recover", List.of()));

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        List<AgentProgressEvent.Type> types = captor.getAllValues().stream()
                .map(AgentProgressEvent::getType)
                .toList();
        assertThat(types).contains(AgentProgressEvent.Type.STEP_FAILED, AgentProgressEvent.Type.ABORTED);
    }

    @Test
    void execute_stepFails_criticIsConsulted() throws Exception {
        AgentPlan plan = singlePhasePlan("FILE_EDIT");
        when(mockDispatcher.dispatch(any(), any())).thenReturn(ToolResult.failure("error"));
        when(mockCritic.evaluate(any())).thenReturn(
                new CriticDecision(CriticDecision.Status.ABORT, "unrecoverable", List.of()));

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        verify(mockCritic, times(1)).evaluate(any());
    }

    // -------------------------------------------------------------------------
    // Critic decisions
    // -------------------------------------------------------------------------

    @Test
    void execute_criticAborts_publishesAbortedEvent() throws Exception {
        AgentPlan plan = singlePhasePlan("FILE_READ");
        when(mockDispatcher.dispatch(any(), any())).thenReturn(ToolResult.success("ok"));
        when(mockCritic.evaluate(any())).thenReturn(
                new CriticDecision(CriticDecision.Status.ABORT, "wrong direction", List.of()));

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentProgressEvent::getType)
                .contains(AgentProgressEvent.Type.ABORTED);
    }

    @Test
    void execute_criticAdapts_publishesPlanAdaptedAndContinues() throws Exception {
        // Plan with 2 phases; critic on phase 1 returns ADAPT with 1 revised phase
        Step s1 = new Step("FILE_READ", "read", java.util.Map.of());
        Step s2 = new Step("GIT_STATUS", "status", java.util.Map.of());
        Phase phase1 = new Phase("Phase 1", List.of(s1));
        Phase phase2Original = new Phase("Phase 2 original", List.of(new Step("FILE_DELETE", "delete", java.util.Map.of())));
        Phase phase2Revised = new Phase("Phase 2 revised", List.of(s2));
        AgentPlan plan = new AgentPlan("goal", "reasoning", List.of(phase1, phase2Original));

        when(mockDispatcher.dispatch(any(), any())).thenReturn(ToolResult.success("ok"));
        when(mockCritic.evaluate(any()))
                .thenReturn(new CriticDecision(CriticDecision.Status.ADAPT, "need revision", List.of(phase2Revised)))
                .thenReturn(ok());

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        ArgumentCaptor<AgentProgressEvent> captor = ArgumentCaptor.forClass(AgentProgressEvent.class);
        verify(mockNotifier, atLeastOnce()).onProgress(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentProgressEvent::getType)
                .contains(AgentProgressEvent.Type.PLAN_ADAPTED, AgentProgressEvent.Type.COMPLETED);
    }

    @Test
    void execute_multiplePhases_criticCalledOncePerPhase() throws Exception {
        Step s = new Step("GIT_STATUS", "status", java.util.Map.of());
        AgentPlan plan = new AgentPlan("goal", "r",
                List.of(new Phase("p1", List.of(s)), new Phase("p2", List.of(s))));
        when(mockDispatcher.dispatch(any(), any())).thenReturn(ToolResult.success("ok"));
        when(mockCritic.evaluate(any())).thenReturn(ok());

        orchestrator.execute(plan, mockDispatcher, mockCritic).get();

        verify(mockCritic, times(2)).evaluate(any());
    }
}
