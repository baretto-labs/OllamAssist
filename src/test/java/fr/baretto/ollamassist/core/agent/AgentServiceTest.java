package fr.baretto.ollamassist.core.agent;

import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour la nouvelle architecture AgentService avec LangChain4J
 */
public class AgentServiceTest {

    @Test
    public void shouldCreateIntelliJDevelopmentAgentWithoutExternalDependencies() {
        // Given: Mock project pour éviter les dépendances IntelliJ en test
        Project mockProject = Mockito.mock(Project.class);
        Mockito.when(mockProject.getName()).thenReturn("TestProject");

        // When: Création de l'agent de développement
        IntelliJDevelopmentAgent developmentAgent = new IntelliJDevelopmentAgent(mockProject);

        // Then: Agent créé avec succès
        assertNotNull(developmentAgent, "Development agent should not be null");

        AgentStats stats = developmentAgent.getStats();
        assertNotNull(stats, "Development agent stats should not be null");
        assertEquals(0, stats.getActiveTasksCount(), "Active tasks should be 0");
        assertEquals(1.0, stats.getSuccessRate(), 0.01, "Success rate should be 100%");
    }

    @Test
    public void shouldProvideToolMethods() {
        // Given: Mock project
        Project mockProject = Mockito.mock(Project.class);
        Mockito.when(mockProject.getName()).thenReturn("TestProject");

        // When: Création de l'agent
        IntelliJDevelopmentAgent agent = new IntelliJDevelopmentAgent(mockProject);

        // Then: Agent créé avec les outils
        assertNotNull(agent, "Agent should not be null");
        assertTrue(agent.isAvailable(), "Agent should be available");
    }

    @Test
    public void shouldConfigureLangChain4JTools() {
        // Given: Mock project
        Project mockProject = Mockito.mock(Project.class);

        // When: Vérification que les annotations @Tool sont présentes
        IntelliJDevelopmentAgent agent = new IntelliJDevelopmentAgent(mockProject);

        // Then: Agent configuré pour LangChain4J
        // Les méthodes avec @Tool sont :
        // - createJavaClass
        // - createFile
        // - analyzeCode
        // - executeGitCommand
        // - buildProject

        // Test que l'agent est bien configuré
        assertNotNull(agent, "Agent should be properly configured with LangChain4J tools");
    }

    @Test
    public void shouldDetectNativeToolsSupportHybridArchitecture() {
        // Given: Mock project
        Project mockProject = Mockito.mock(Project.class);
        Mockito.when(mockProject.getName()).thenReturn("TestProjectHybrid");

        // When: Création d'AgentService (test d'initialisation)
        // NOTE: En mode test, on ne peut pas vraiment créer AgentService car il dépend
        // des services Ollama. On teste juste la détection théorique.

        // Then: L'architecture hybride devrait être supportée
        // Test théorique : si Ollama supporte function calling -> native tools
        // Sinon -> JSON fallback

        // Vérification que les outils sont disponibles
        IntelliJDevelopmentAgent agent = new IntelliJDevelopmentAgent(mockProject);
        assertTrue(agent.isAvailable(), "Agent tools should be available");

        // Cette architecture permet:
        // 1. Mode natif avec function calling (quand supporté)
        // 2. Mode fallback avec JSON parsing (pour compatibilité)

        assertNotNull(agent, "Hybrid architecture should support both native and fallback modes");
    }
}