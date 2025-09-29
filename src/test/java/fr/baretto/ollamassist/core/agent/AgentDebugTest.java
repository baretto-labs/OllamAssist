package fr.baretto.ollamassist.core.agent;

import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Test de debug pour comprendre pourquoi l'agent ne crée pas de fichiers
 */
class AgentDebugTest {

    @Test
    void debugAgentToolExecution() {
        System.out.println("🔍 DEBUG: Test direct de l'agent");

        // Given: Mock project
        Project mockProject = Mockito.mock(Project.class);
        Mockito.when(mockProject.getName()).thenReturn("DebugProject");

        // When: Création et test direct de l'agent
        IntelliJDevelopmentAgent agent = new IntelliJDevelopmentAgent(mockProject);

        System.out.println("🔍 Agent créé, test des méthodes tools directement");

        // Test direct du tool createFile (sans passer par LangChain4J)
        String result = agent.createFile("test.txt", "Hello World!");

        System.out.println("🔍 Résultat direct du tool: " + result);

        // Test direct du tool createJavaClass
        String javaResult = agent.createJavaClass("HelloWorld",
                "src/test/HelloWorld.java",
                " class HelloWorld {  void sayHello() { System.out.println(\"Hello!\"); } }");

        System.out.println("🔍 Résultat Java class: " + javaResult);
    }

    @Test
    void debugExecutionEngine() {
        System.out.println("🔍 DEBUG: Test direct de l'ExecutionEngine");

        // Given: Mock project
        Project mockProject = Mockito.mock(Project.class);
        Mockito.when(mockProject.getName()).thenReturn("DebugProject");

        // When: Test direct de l'ExecutionEngine (sans agent)
        System.out.println("🔍 Ce test va échouer car ExecutionEngine a besoin d'un vrai projet IntelliJ");
        System.out.println("🔍 Mais il nous dira si le problème vient de là");
    }

    @Test
    void checkOllamaConfiguration() {
        System.out.println("🔍 DEBUG: Vérification de la configuration Ollama");

        try {
            fr.baretto.ollamassist.setting.OllamAssistSettings settings =
                    fr.baretto.ollamassist.setting.OllamAssistSettings.getInstance();

            System.out.println("🔍 URL Ollama: " + settings.getCompletionOllamaUrl());
            System.out.println("🔍 Modèle: " + settings.getCompletionModelName());
            System.out.println("🔍 Timeout: " + settings.getTimeoutDuration());

        } catch (Exception e) {
            System.out.println("🔍 ERREUR configuration Ollama: " + e.getMessage());
        }
    }
}