package fr.baretto.ollamassist.agent.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for StepState enum.
 */
class StepStateTest {

    @Test
    void stepState_shouldHaveAllExpectedValues() {
        assertThat(StepState.values()).containsExactlyInAnyOrder(
            StepState.PENDING,
            StepState.RUNNING,
            StepState.COMPLETED,
            StepState.FAILED
        );
    }
}
