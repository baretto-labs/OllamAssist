package fr.baretto.ollamassist.agent.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SourceType enum.
 */
class SourceTypeTest {

    @Test
    void sourceType_shouldHaveAllExpectedValues() {
        assertThat(SourceType.values()).containsExactlyInAnyOrder(
            SourceType.FILE,
            SourceType.URL,
            SourceType.CLASS,
            SourceType.COMMIT,
            SourceType.DOCUMENTATION,
            SourceType.SNIPPET,
            SourceType.EMBEDDING
        );
    }
}
