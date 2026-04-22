package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.agent.tools.ToolRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies ToolRegistry constants used by plan validation and UX-3 auto-validate mode.
 */
class ToolRegistryTest {

    @Test
    void readOnlyToolIds_areSubsetOfKnownToolIds() {
        assertThat(ToolRegistry.KNOWN_TOOL_IDS)
                .containsAll(ToolRegistry.READ_ONLY_TOOL_IDS);
    }

    @Test
    void readOnlyToolIds_doNotContainMutatingTools() {
        assertThat(ToolRegistry.READ_ONLY_TOOL_IDS)
                .doesNotContain("FILE_WRITE", "FILE_EDIT", "FILE_DELETE", "RUN_COMMAND", "OPEN_IN_EDITOR");
    }

    @Test
    void readOnlyToolIds_containExpectedReadTools() {
        assertThat(ToolRegistry.READ_ONLY_TOOL_IDS)
                .contains("FILE_READ", "FILE_FIND", "CODE_SEARCH", "GIT_STATUS", "GIT_DIFF",
                        "GET_CURRENT_FILE", "SEARCH_KNOWLEDGE");
    }
}
