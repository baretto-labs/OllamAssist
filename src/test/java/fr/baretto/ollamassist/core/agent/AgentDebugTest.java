package fr.baretto.ollamassist.core.agent;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test de debug pour comprendre pourquoi l'agent ne crée pas de fichiers
 */
class AgentDebugTest extends BasePlatformTestCase {

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Test
    void debugAgentToolExecution() {
        System.out.println("DEBUG: Test direct de l'agent");
        System.out.println("Project name: " + getProject().getName());

        // When: Création et test direct de l'agent
        IntelliJDevelopmentAgent agent = new IntelliJDevelopmentAgent(getProject());

        System.out.println("Agent créé, test des méthodes tools directement");

        // Test direct du tool createFile (sans passer par LangChain4J)
        String result = agent.createFile("test.txt", "Hello World!");

        System.out.println("Résultat direct du tool: " + result);

        // Test direct du tool createJavaClass
        String javaResult = agent.createJavaClass("HelloWorld",
                "src/test/HelloWorld.java",
                "public class HelloWorld { public void sayHello() { System.out.println(\"Hello!\"); } }");

        System.out.println("Résultat Java class: " + javaResult);
        Assertions.assertNotNull(javaResult);
    }

    @Test
    void checkOllamaConfiguration() {
        System.out.println("DEBUG: Vérification de la configuration Ollama");

        try {
            fr.baretto.ollamassist.setting.OllamAssistSettings settings =
                    fr.baretto.ollamassist.setting.OllamAssistSettings.getInstance();

            System.out.println("URL Ollama: " + settings.getCompletionOllamaUrl());
            System.out.println("Modèle: " + settings.getCompletionModelName());
            System.out.println("Timeout: " + settings.getTimeoutDuration());
            Assertions.assertNotNull(settings);
        } catch (Exception e) {
            System.out.println("ERREUR configuration Ollama: " + e.getMessage());
        }
    }
}
