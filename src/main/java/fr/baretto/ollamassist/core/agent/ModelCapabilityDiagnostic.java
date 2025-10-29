package fr.baretto.ollamassist.core.agent;

import com.intellij.openapi.project.Project;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Diagnostic des capacités des modèles Ollama pour le function calling
 */
@Slf4j
public class ModelCapabilityDiagnostic {

    private final Project project;
    private final OllamAssistSettings settings;

    public ModelCapabilityDiagnostic(Project project) {
        this.project = project;
        this.settings = OllamAssistSettings.getInstance();
    }

    /**
     * Test complet des capacités du modèle actuel
     */
    public ModelCapabilityReport diagnoseCurrentModel() {
        String modelName = settings.getCompletionModelName();
        String baseUrl = settings.getCompletionOllamaUrl();

        log.info("DIAGNOSTIC: Testing model capabilities for: {}", modelName);

        return diagnoseModel(modelName, baseUrl);
    }

    /**
     * Test des capacités d'un modèle spécifique
     */
    public ModelCapabilityReport diagnoseModel(String modelName, String baseUrl) {
        ModelCapabilityReport.ModelCapabilityReportBuilder report = ModelCapabilityReport.builder()
                .modelName(modelName)
                .baseUrl(baseUrl);

        try {
            // 1. Test de connectivité de base
            boolean basicConnectivity = testBasicConnectivity(modelName, baseUrl);
            report.basicConnectivity(basicConnectivity);

            if (!basicConnectivity) {
                return report
                        .functionCallingSupported(false)
                        .recommendedForAgent(false)
                        .issues(List.of("Cannot connect to model"))
                        .build();
            }

            // 2. Test de function calling
            boolean functionCalling = testFunctionCalling(modelName, baseUrl);
            report.functionCallingSupported(functionCalling);

            // 3. Test de structured output (fallback)
            boolean structuredOutput = testStructuredOutput(modelName, baseUrl);
            report.structuredOutputSupported(structuredOutput);

            // 4. Évaluation finale
            boolean recommended = evaluateRecommendation(modelName, functionCalling, structuredOutput);
            report.recommendedForAgent(recommended);

            // 5. Issues et recommandations
            List<String> issues = identifyIssues(modelName, functionCalling, structuredOutput);
            report.issues(issues);

            List<String> recommendations = generateRecommendations(modelName, functionCalling, structuredOutput);
            report.recommendations(recommendations);

        } catch (Exception e) {
            log.error("Error during model diagnostic for {}: {}", modelName, e.getMessage());
            return report
                    .basicConnectivity(false)
                    .functionCallingSupported(false)
                    .structuredOutputSupported(false)
                    .recommendedForAgent(false)
                    .issues(List.of("Diagnostic failed: " + e.getMessage()))
                    .build();
        }

        ModelCapabilityReport finalReport = report.build();
        log.info("DIAGNOSTIC COMPLETE for {}: Function Calling={}, Structured Output={}, Recommended={}",
                modelName, finalReport.isFunctionCallingSupported(),
                finalReport.isStructuredOutputSupported(), finalReport.isRecommendedForAgent());

        return finalReport;
    }

    /**
     * Test de connectivité de base
     */
    private boolean testBasicConnectivity(String modelName, String baseUrl) {
        try {
            OllamaChatModel chatModel = OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

            dev.langchain4j.model.chat.response.ChatResponse response = chatModel.chat(
                    dev.langchain4j.data.message.UserMessage.from("Hello")
            );
            return response != null && response.aiMessage() != null && !response.aiMessage().text().trim().isEmpty();

        } catch (Exception e) {
            log.warn("Basic connectivity test failed for {}: {}", modelName, e.getMessage());
            return false;
        }
    }

    /**
     * Test de function calling avec LangChain4J tools
     */
    private boolean testFunctionCalling(String modelName, String baseUrl) {
        try {
            OllamaChatModel chatModel = OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(java.time.Duration.ofSeconds(15))
                    .build();

            // Créer un simple test tool
            TestAgent testAgent = AiServices.builder(TestAgent.class)
                    .chatModel(chatModel)
                    .tools(new SimpleTestTool())
                    .build();

            // Test simple function calling
            String result = testAgent.testFunctionCall("test");

            // Si on arrive ici sans exception, function calling fonctionne
            log.info("Function calling test successful for {}: {}", modelName, result);
            return true;

        } catch (Exception e) {
            log.warn("Function calling test failed for {}: {}", modelName, e.getMessage());
            return false;
        }
    }

    /**
     * Test de structured output JSON
     */
    private boolean testStructuredOutput(String modelName, String baseUrl) {
        try {
            OllamaChatModel chatModel = OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(java.time.Duration.ofSeconds(15))
                    .build();

            String structuredPrompt = """
                    Respond with valid JSON in this exact format:
                    {"test": true, "message": "structured output works"}
                    """;

            dev.langchain4j.model.chat.response.ChatResponse response = chatModel.chat(
                    dev.langchain4j.data.message.UserMessage.from(structuredPrompt)
            );
            String responseText = response.aiMessage().text();

            // Vérifier si la réponse contient du JSON valide
            return responseText != null &&
                    (responseText.contains("{") && responseText.contains("}")) &&
                    (responseText.contains("test") || responseText.contains("message"));

        } catch (Exception e) {
            log.warn("Structured output test failed for {}: {}", modelName, e.getMessage());
            return false;
        }
    }

    /**
     * Évalue si le modèle est recommandé pour l'agent
     */
    private boolean evaluateRecommendation(String modelName, boolean functionCalling, boolean structuredOutput) {
        // Modèles spécifiquement recommandés
        if (isRecommendedModel(modelName)) {
            return functionCalling || structuredOutput;
        }

        // Pour les autres modèles, besoin d'au moins structured output
        return structuredOutput;
    }

    /**
     * Vérifie si c'est un modèle spécifiquement recommandé
     */
    private boolean isRecommendedModel(String modelName) {
        String lower = modelName.toLowerCase();
        return lower.contains("gpt-oss") ||
                lower.contains("mistral") ||
                lower.contains("llama3.2") ||
                lower.contains("llama3.3") ||
                lower.contains("qwen") ||
                lower.contains("deepseek");
    }

    /**
     * Identifie les problèmes avec le modèle
     */
    private List<String> identifyIssues(String modelName, boolean functionCalling, boolean structuredOutput) {
        List<String> issues = new java.util.ArrayList<>();

        if (!functionCalling && !structuredOutput) {
            issues.add("Model does not support function calling or structured output");
        } else if (!functionCalling) {
            issues.add("Function calling not supported - will use JSON fallback");
        }

        if (modelName.equals("llama3.1")) {
            issues.add("llama3.1 has known issues with function calling - consider upgrading");
        }

        return issues;
    }

    /**
     * Génère des recommandations
     */
    private List<String> generateRecommendations(String modelName, boolean functionCalling, boolean structuredOutput) {
        List<String> recommendations = new java.util.ArrayList<>();

        if (!functionCalling && !structuredOutput) {
            recommendations.add("Switch to a more recent model like gpt-oss, llama3.2, or mistral");
        } else if (!functionCalling) {
            recommendations.add("Consider upgrading to a model with better function calling support");
        }

        if (modelName.equals("llama3.1")) {
            recommendations.add("Recommended models for agent mode:");
            recommendations.add("- gpt-oss (optimized for Ollama)");
            recommendations.add("- llama3.2 (latest Llama version)");
            recommendations.add("- mistral (excellent function calling support)");
            recommendations.add("- qwen2.5 (very capable recent model)");
        }

        return recommendations;
    }

    /**
     * Interface pour test agent
     */
    public interface TestAgent {
        String testFunctionCall(String input);
    }

    /**
     * Simple tool pour tester function calling
     */
    public static class SimpleTestTool {
        @dev.langchain4j.agent.tool.Tool("Simple test tool")
        public String testTool(@dev.langchain4j.agent.tool.P("test input") String input) {
            return "Tool called successfully with: " + input;
        }
    }

    /**
     * Rapport de diagnostic
     */
    @lombok.Builder
    @lombok.Data
    public static class ModelCapabilityReport {
        private String modelName;
        private String baseUrl;
        private boolean basicConnectivity;
        private boolean functionCallingSupported;
        private boolean structuredOutputSupported;
        private boolean recommendedForAgent;
        private List<String> issues;
        private List<String> recommendations;

        public String generateReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("Model Capability Report for: ").append(modelName).append("\n\n");

            sb.append("Basic Connectivity: ").append(basicConnectivity ? "" : "").append("\n");
            sb.append("Function Calling: ").append(functionCallingSupported ? "" : "").append("\n");
            sb.append("Structured Output: ").append(structuredOutputSupported ? "" : "").append("\n");
            sb.append("Recommended for Agent: ").append(recommendedForAgent ? "" : "").append("\n\n");

            if (!issues.isEmpty()) {
                sb.append("️ Issues:\n");
                issues.forEach(issue -> sb.append("  - ").append(issue).append("\n"));
                sb.append("\n");
            }

            if (!recommendations.isEmpty()) {
                sb.append("Recommendations:\n");
                recommendations.forEach(rec -> sb.append("  - ").append(rec).append("\n"));
            }

            return sb.toString();
        }
    }
}