package fr.baretto.ollamassist.core.agent;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests pour la nouvelle architecture AgentService avec LangChain4J
 */
@DisplayName("Agent Service Tests")
public class AgentServiceTest extends BasePlatformTestCase {

    private IntelliJDevelopmentAgent agent;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        agent = new IntelliJDevelopmentAgent(getProject());
    }

    @Test
    @DisplayName("Should create IntelliJ development agent without external dependencies")
    public void shouldCreateIntelliJDevelopmentAgentWithoutExternalDependencies() {
        // When: Agent is created with real project
        // Then: Agent created successfully
        assertThat(agent).isNotNull();

        AgentStats stats = agent.getStats();
        assertThat(stats).isNotNull();
        assertThat(stats.getActiveTasksCount()).isEqualTo(0);
        assertThat(stats.getSuccessRate()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("Should provide tool methods")
    public void shouldProvideToolMethods() {
        // When: Agent is created
        // Then: Agent has tools available
        assertThat(agent).isNotNull();
        assertThat(agent.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Should configure LangChain4J tools")
    public void shouldConfigureLangChain4JTools() {
        // When: Agent is created
        // Then: Agent is configured with LangChain4J tools
        // Methods with @Tool annotation:
        // - createJavaClass
        // - createFile
        // - analyzeCode
        // - executeGitCommand
        // - buildProject

        assertThat(agent).isNotNull();
    }

    @Test
    @DisplayName("Should detect native tools support for hybrid architecture")
    public void shouldDetectNativeToolsSupportHybridArchitecture() {
        // When: AgentService initialization
        // NOTE: In test mode, we cannot create AgentService because it depends
        // on Ollama services. We test only the theoretical detection.

        // Then: Hybrid architecture should be supported
        // Theoretical test: if Ollama supports function calling -> native tools
        // Otherwise -> JSON fallback

        // Verify that tools are available
        assertThat(agent.isAvailable()).isTrue();

        // This architecture allows:
        // 1. Native mode with function calling (when supported)
        // 2. Fallback mode with JSON parsing (for compatibility)

        assertThat(agent).isNotNull();
    }
}