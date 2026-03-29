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

    // --- Model change invalidation ---

    @Test
    void constructor_subscribesToChatModelModifiedNotifier() {
        verify(mockBus.connect(orchestrator)).subscribe(eq(ChatModelModifiedNotifier.TOPIC), any());
    }
}
