package fr.baretto.ollamassist.core.agent;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

/**
 * Test pour vérifier si les annotations @Tool sont détectées correctement
 */
public class LangChain4JToolTest {

    @Test
    public void verifyToolAnnotationsArePresent() {
        System.out.println("VERIFICATION: Annotations @Tool dans IntelliJDevelopmentAgent");

        Class<IntelliJDevelopmentAgent> agentClass = IntelliJDevelopmentAgent.class;
        Method[] methods = agentClass.getMethods();

        int toolCount = 0;
        for (Method method : methods) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation != null) {
                toolCount++;
                System.out.println("Tool trouvé: " + method.getName() + " - " + toolAnnotation.value());

                // Vérifier les paramètres
                var paramTypes = method.getParameterTypes();
                var paramAnnotations = method.getParameterAnnotations();

                for (int i = 0; i < paramTypes.length; i++) {
                    boolean hasP = false;
                    for (var annotation : paramAnnotations[i]) {
                        if (annotation instanceof P p) {
                            hasP = true;
                            System.out.println("  Param " + i + ": " + paramTypes[i].getSimpleName() + " - " + p.value());
                        }
                    }
                    if (!hasP) {
                        System.out.println("  ️ Param " + i + ": " + paramTypes[i].getSimpleName() + " (pas d'annotation @P)");
                    }
                }
            }
        }

        System.out.println("Total tools trouvés: " + toolCount);

        if (toolCount == 0) {
            System.out.println("PROBLEME: Aucun tool @Tool trouvé!");
        } else {
            System.out.println("" + toolCount + " tools détectés correctement");
        }
    }

    @Test
    public void verifySimpleToolClass() {
        System.out.println("VERIFICATION: Création d'une classe de test avec tool simple");

        // Créer une classe simple avec un tool pour test
        SimpleTestTool testTool = new SimpleTestTool();

        System.out.println("SimpleTestTool créé");

        // Vérifier que la méthode a l'annotation
        try {
            Method testMethod = SimpleTestTool.class.getMethod("testTool", String.class);
            Tool annotation = testMethod.getAnnotation(Tool.class);

            if (annotation != null) {
                System.out.println("Annotation @Tool détectée: " + annotation.value());
            } else {
                System.out.println("Annotation @Tool non détectée");
            }
        } catch (NoSuchMethodException e) {
            System.out.println("Méthode testTool non trouvée: " + e.getMessage());
        }
    }

    // Classe de test simple
    public static class SimpleTestTool {
        @Tool("Test tool for debugging")
        public String testTool(@P("Input parameter") String input) {
            System.out.println("TEST TOOL CALLED: " + input);
            return "Tool result: " + input;
        }
    }
}