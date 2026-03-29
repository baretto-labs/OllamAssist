package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.agent.critic.CriticDecision;
import fr.baretto.ollamassist.agent.plan.Phase;
import fr.baretto.ollamassist.agent.plan.Step;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CriticDecisionTest {

    @Test
    void nullStatus_defaultsToABORT() {
        CriticDecision decision = new CriticDecision(null, "unknown", List.of());

        assertThat(decision.getStatus()).isEqualTo(CriticDecision.Status.ABORT);
    }

    @Test
    void nullReasoning_defaultsToEmpty() {
        CriticDecision decision = new CriticDecision(CriticDecision.Status.OK, null, List.of());

        assertThat(decision.getReasoning()).isEmpty();
    }

    @Test
    void nullRevisedPhases_defaultsToEmptyList() {
        CriticDecision decision = new CriticDecision(CriticDecision.Status.ADAPT, "adapt", null);

        assertThat(decision.getRevisedPhases()).isEmpty();
    }

    @Test
    void revisedPhases_areUnmodifiable() {
        Phase phase = new Phase("p", List.of(new Step("GIT_STATUS", "s", null)));
        CriticDecision decision = new CriticDecision(CriticDecision.Status.ADAPT, "adapt", List.of(phase));

        assertThatThrownBy(() -> decision.getRevisedPhases().add(new Phase("extra", List.of())))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void isOk_trueOnlyForOK() {
        assertThat(new CriticDecision(CriticDecision.Status.OK, "", List.of()).isOk()).isTrue();
        assertThat(new CriticDecision(CriticDecision.Status.ADAPT, "", List.of()).isOk()).isFalse();
        assertThat(new CriticDecision(CriticDecision.Status.ABORT, "", List.of()).isOk()).isFalse();
    }

    @Test
    void shouldAbort_trueOnlyForABORT() {
        assertThat(new CriticDecision(CriticDecision.Status.ABORT, "", List.of()).shouldAbort()).isTrue();
        assertThat(new CriticDecision(CriticDecision.Status.OK, "", List.of()).shouldAbort()).isFalse();
    }

    @Test
    void shouldAdapt_trueOnlyForADAPT() {
        assertThat(new CriticDecision(CriticDecision.Status.ADAPT, "", List.of()).shouldAdapt()).isTrue();
        assertThat(new CriticDecision(CriticDecision.Status.OK, "", List.of()).shouldAdapt()).isFalse();
    }
}
