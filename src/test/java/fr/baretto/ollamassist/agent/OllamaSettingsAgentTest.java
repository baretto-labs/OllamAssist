package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.setting.OllamaSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for agent-specific OllamaSettings fields (TECH-3 and UX-3).
 * Does NOT require the IntelliJ platform — tests the State object directly.
 */
class OllamaSettingsAgentTest {

    private OllamaSettings.State state;

    @BeforeEach
    void setUp() {
        state = new OllamaSettings.State();
    }

    // -------------------------------------------------------------------------
    // TECH-3: Separate planner / critic model names
    // -------------------------------------------------------------------------

    @Test
    void agentPlannerModelName_defaultsToEmpty() {
        assertThat(state.agentPlannerModelName).isEmpty();
    }

    @Test
    void agentCriticModelName_defaultsToEmpty() {
        assertThat(state.agentCriticModelName).isEmpty();
    }

    @Test
    void agentAutoValidateMode_defaultsToManual() {
        assertThat(state.agentAutoValidateMode).isEqualTo("MANUAL");
    }

    @Test
    void agentToolTimeoutSeconds_defaultsToZero() {
        // 0 means "use default 120s" — verified by OllamaSettings.getAgentToolTimeoutSeconds()
        assertThat(state.agentToolTimeoutSeconds).isZero();
    }

    // -------------------------------------------------------------------------
    // UX-3: Auto-validate mode persistence
    // -------------------------------------------------------------------------

    @Test
    void autoValidateMode_validValues_areAccepted() {
        for (String mode : new String[]{"MANUAL", "SMART", "FULL_AUTO"}) {
            state.agentAutoValidateMode = mode;
            assertThat(state.agentAutoValidateMode).isEqualTo(mode);
        }
    }
}
