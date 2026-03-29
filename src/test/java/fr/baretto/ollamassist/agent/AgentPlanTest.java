package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.agent.plan.AgentPlan;
import fr.baretto.ollamassist.agent.plan.Phase;
import fr.baretto.ollamassist.agent.plan.Step;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentPlanTest {

    // --- Step ---

    @Test
    void step_rejectsNullToolId() {
        assertThatThrownBy(() -> new Step(null, "desc", Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void step_rejectsNullDescription() {
        assertThatThrownBy(() -> new Step("FILE_READ", null, Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void step_nullParams_defaultsToEmptyMap() {
        Step step = new Step("FILE_READ", "Read main class", null);

        assertThat(step.getParams()).isEmpty();
    }

    @Test
    void step_params_areUnmodifiable() {
        Step step = new Step("FILE_EDIT", "Edit file", Map.of("path", "Foo.java"));

        assertThatThrownBy(() -> step.getParams().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void step_holdsValues() {
        Map<String, Object> params = Map.of("path", "src/Main.java");
        Step step = new Step("FILE_READ", "Read main", params);

        assertThat(step.getToolId()).isEqualTo("FILE_READ");
        assertThat(step.getDescription()).isEqualTo("Read main");
        assertThat(step.getParams()).containsEntry("path", "src/Main.java");
    }

    // --- Phase ---

    @Test
    void phase_rejectsNullDescription() {
        assertThatThrownBy(() -> new Phase(null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void phase_nullSteps_defaultsToEmptyList() {
        Phase phase = new Phase("Explore codebase", null);

        assertThat(phase.getSteps()).isEmpty();
    }

    @Test
    void phase_steps_areUnmodifiable() {
        Phase phase = new Phase("Read files", List.of(new Step("FILE_READ", "read", null)));

        assertThatThrownBy(() -> phase.getSteps().add(new Step("GIT_STATUS", "status", null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- AgentPlan ---

    @Test
    void agentPlan_rejectsNullGoal() {
        assertThatThrownBy(() -> new AgentPlan(null, "reason", List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void agentPlan_nullReasoning_defaultsToEmpty() {
        AgentPlan plan = new AgentPlan("Fix bug", null, List.of());

        assertThat(plan.getReasoning()).isEmpty();
    }

    @Test
    void agentPlan_isEmpty_whenNoPhases() {
        AgentPlan plan = new AgentPlan("goal", "reasoning", List.of());

        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    void agentPlan_isEmpty_falseWhenHasPhases() {
        Phase phase = new Phase("Phase 1", List.of(new Step("GIT_STATUS", "check status", null)));
        AgentPlan plan = new AgentPlan("goal", "reasoning", List.of(phase));

        assertThat(plan.isEmpty()).isFalse();
    }

    @Test
    void agentPlan_totalSteps_sumsAcrossPhases() {
        Step s1 = new Step("FILE_READ", "read", null);
        Step s2 = new Step("FILE_EDIT", "edit", null);
        Step s3 = new Step("GIT_STATUS", "status", null);
        Phase phase1 = new Phase("Phase 1", List.of(s1, s2));
        Phase phase2 = new Phase("Phase 2", List.of(s3));
        AgentPlan plan = new AgentPlan("goal", "reasoning", List.of(phase1, phase2));

        assertThat(plan.totalSteps()).isEqualTo(3);
    }

    @Test
    void agentPlan_phases_areUnmodifiable() {
        Phase phase = new Phase("p", List.of());
        AgentPlan plan = new AgentPlan("goal", "reasoning", List.of(phase));

        assertThatThrownBy(() -> plan.getPhases().add(new Phase("extra", List.of())))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- AgentProgressEvent factory methods ---

    @Test
    void progressEvent_planning_hasCorrectType() {
        AgentProgressEvent event = AgentProgressEvent.planning();

        assertThat(event.getType()).isEqualTo(AgentProgressEvent.Type.PLANNING);
        assertThat(event.getPlan()).isNull();
        assertThat(event.getStep()).isNull();
    }

    @Test
    void progressEvent_planReady_carriesPlan() {
        AgentPlan plan = new AgentPlan("goal", "reasoning",
                List.of(new Phase("p", List.of(new Step("GIT_STATUS", "s", null)))));

        AgentProgressEvent event = AgentProgressEvent.planReady(plan);

        assertThat(event.getType()).isEqualTo(AgentProgressEvent.Type.PLAN_READY);
        assertThat(event.getPlan()).isSameAs(plan);
        assertThat(event.getMessage()).contains("1 steps");
    }

    @Test
    void progressEvent_stepStarted_carriesStep() {
        Step step = new Step("FILE_READ", "read Foo.java", null);

        AgentProgressEvent event = AgentProgressEvent.stepStarted(step);

        assertThat(event.getType()).isEqualTo(AgentProgressEvent.Type.STEP_STARTED);
        assertThat(event.getStep()).isSameAs(step);
        assertThat(event.getMessage()).isEqualTo("read Foo.java");
    }

    @Test
    void progressEvent_aborted_includesReason() {
        AgentProgressEvent event = AgentProgressEvent.aborted("timeout");

        assertThat(event.getType()).isEqualTo(AgentProgressEvent.Type.ABORTED);
        assertThat(event.getMessage()).contains("timeout");
    }
}
