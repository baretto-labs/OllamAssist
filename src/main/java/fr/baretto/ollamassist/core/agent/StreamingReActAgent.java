package fr.baretto.ollamassist.core.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.core.agent.execution.ExecutionEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent ReAct avec structured output et streaming
 * Solution de contournement pour Ollama qui ne supporte pas encore les LangChain4J tools
 */
@Slf4j
public class StreamingReActAgent {

    private final Project project;
    private final OllamaService ollamaService;
    private final IntelliJDevelopmentAgent developmentAgent;
    private final ExecutionEngine executionEngine;
    private final ObjectMapper objectMapper;

    // Callbacks pour le streaming
    private Consumer<String> onThinking;
    private Consumer<String> onAction;
    private Consumer<String> onObservation;
    private Consumer<String> onFinalAnswer;
    private Consumer<String> onError;

    public StreamingReActAgent(Project project) {
        this.project = project;
        this.ollamaService = project.getService(OllamaService.class);
        this.developmentAgent = new IntelliJDevelopmentAgent(project);
        this.executionEngine = new ExecutionEngine(project);
        this.objectMapper = new ObjectMapper();

        log.info("StreamingReActAgent initialized for project: {}", project.getName());
    }

    /**
     * Configure les callbacks de streaming
     */
    public StreamingReActAgent withCallbacks(
            Consumer<String> onThinking,
            Consumer<String> onAction,
            Consumer<String> onObservation,
            Consumer<String> onFinalAnswer,
            Consumer<String> onError) {

        this.onThinking = onThinking;
        this.onAction = onAction;
        this.onObservation = onObservation;
        this.onFinalAnswer = onFinalAnswer;
        this.onError = onError;
        return this;
    }

    /**
     * Exécute une requête utilisateur avec ReAct streaming
     */
    public void executeWithStreaming(String userRequest) {
        log.info("STREAMING REACT: Starting execution for: {}", userRequest);

        try {
            executeReActCycle(userRequest, null, 0);
        } catch (Exception e) {
            log.error("Error in ReAct execution", e);
            if (onError != null) {
                onError.accept("Erreur lors de l'exécution: " + e.getMessage());
            }
        }
    }

    /**
     * Exécute un cycle ReAct complet avec gestion des itérations
     */
    private void executeReActCycle(String userRequest, String previousObservation, int iteration) {
        final int MAX_ITERATIONS = 10; // Éviter les boucles infinies

        if (iteration >= MAX_ITERATIONS) {
            log.warn("Max iterations reached for ReAct cycle");
            if (onFinalAnswer != null) {
                onFinalAnswer.accept("Limite d'itérations atteinte. Tâche partiellement complétée.");
            }
            return;
        }

        log.info("ReAct iteration {}/{}", iteration + 1, MAX_ITERATIONS);

        // Construire le prompt ReAct structuré
        String structuredPrompt = buildStructuredReActPrompt(userRequest, previousObservation, iteration);

        // Obtenir la réponse du LLM (sans streaming pour parsing JSON)
        if (ollamaService != null && ollamaService.getAssistant() != null) {

            // Stream thinking status
            if (onThinking != null) {
                onThinking.accept("Analyzing request and planning actions...");
            }

            ollamaService.getAssistant().chat(structuredPrompt)
                    .onCompleteResponse(chatResponse -> {
                        try {
                            // Parser la réponse structurée
                            StructuredAgentResponse response = parseStructuredResponse(chatResponse.aiMessage().text());

                            if (response != null) {
                                processStructuredResponse(response, userRequest, iteration);
                            } else {
                                // Fallback si parsing échoue
                                if (onFinalAnswer != null) {
                                    onFinalAnswer.accept(chatResponse.aiMessage().text());
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error processing structured response", e);
                            if (onError != null) {
                                onError.accept("Erreur lors du traitement de la réponse: " + e.getMessage());
                            }
                        }
                    })
                    .onError(throwable -> {
                        log.error("Error in LLM communication", throwable);
                        if (onError != null) {
                            onError.accept("Erreur de communication avec le LLM: " + throwable.getMessage());
                        }
                    })
                    .start();
        } else {
            log.error("OllamaService not available");
            if (onError != null) {
                onError.accept("Service Ollama non disponible");
            }
        }
    }

    /**
     * Traite une réponse structurée et continue le cycle ReAct si nécessaire
     */
    private void processStructuredResponse(StructuredAgentResponse response, String userRequest, int iteration) {
        // 1. Afficher le thinking
        if (response.getThinking() != null && onThinking != null) {
            onThinking.accept(response.getThinking());
        }

        // 2. Exécuter l'action si présente
        if (response.hasAction()) {
            executeAction(response, userRequest, iteration);
        } else if (response.hasCompleteFinalAnswer()) {
            // 3. Réponse finale
            if (onFinalAnswer != null) {
                onFinalAnswer.accept(response.getFinalAnswer());
            }
        } else {
            // 4. Pas d'action et pas de réponse finale - problème
            log.warn("No action or final answer in structured response");
            if (onFinalAnswer != null) {
                onFinalAnswer.accept("Réponse incomplète du modèle.");
            }
        }
    }

    /**
     * Exécute une action et continue le cycle ReAct
     */
    private void executeAction(StructuredAgentResponse response, String userRequest, int iteration) {
        StructuredAgentResponse.AgentAction action = response.getAction();

        // Stream action status
        notifyActionStart(action);

        try {
            String observation = executeToolAction(action);
            handleSuccessfulExecution(response, userRequest, iteration, observation);
        } catch (Exception e) {
            handleExecutionError(response, userRequest, iteration, action, e);
        }
    }

    /**
     * Notifie le début d'une action
     */
    private void notifyActionStart(StructuredAgentResponse.AgentAction action) {
        if (onAction != null) {
            onAction.accept("Executing: " + action.getTool() + " - " + action.getReasoning());
        }
    }

    /**
     * Gère l'exécution réussie d'une action
     */
    private void handleSuccessfulExecution(StructuredAgentResponse response, String userRequest, int iteration, String observation) {
        // Stream observation
        if (onObservation != null) {
            onObservation.accept(observation);
        }

        // Continuer le cycle ReAct si nécessaire
        if (response.shouldContinue()) {
            executeReActCycle(userRequest, observation, iteration + 1);
        } else {
            // Cycle terminé
            if (onFinalAnswer != null) {
                onFinalAnswer.accept(observation);
            }
        }
    }

    /**
     * Gère les erreurs d'exécution d'action
     */
    private void handleExecutionError(StructuredAgentResponse response, String userRequest, int iteration,
                                      StructuredAgentResponse.AgentAction action, Exception e) {
        log.error("Error executing action: {}", action.getTool(), e);
        String errorObservation = "Erreur lors de l'exécution de " + action.getTool() + ": " + e.getMessage();

        if (onObservation != null) {
            onObservation.accept(errorObservation);
        }

        // Continuer malgré l'erreur pour permettre la récupération
        if (response.shouldContinue() && iteration < 8) { // Réduire les itérations en cas d'erreur
            executeReActCycle(userRequest, errorObservation, iteration + 1);
        } else {
            if (onFinalAnswer != null) {
                onFinalAnswer.accept("Tâche interrompue due à des erreurs.");
            }
        }
    }

    /**
     * Exécute une action d'outil spécifique
     */
    private String executeToolAction(StructuredAgentResponse.AgentAction action) {
        String tool = action.getTool();
        Map<String, Object> params = action.getParameters();

        log.info("Executing tool: {} with params: {}", tool, params);

        return switch (tool.toLowerCase()) {
            case "createjavaclass" -> executeCreateJavaClass(params);
            case "createfile" -> executeCreateFile(params);
            case "compileandcheckerrors" -> executeCompileAndCheckErrors();
            case "getcompilationdiagnostics" -> executeGetCompilationDiagnostics();
            case "executegitcommand" -> executeGitCommand(params);
            case "buildproject" -> executeBuildProject(params);
            case "analyzecode" -> executeAnalyzeCode(params);
            default -> "Outil non reconnu: " + tool;
        };
    }

    // Méthodes d'exécution des outils (délègent au developmentAgent)
    private String executeCreateJavaClass(Map<String, Object> params) {
        String className = (String) params.get("className");
        String filePath = (String) params.get("filePath");
        String content = (String) params.get("content");
        return developmentAgent.createJavaClass(className, filePath, content);
    }

    private String executeCreateFile(Map<String, Object> params) {
        String filePath = (String) params.get("filePath");
        String content = (String) params.get("content");
        return developmentAgent.createFile(filePath, content);
    }

    private String executeCompileAndCheckErrors() {
        return developmentAgent.compileAndCheckErrors();
    }

    private String executeGetCompilationDiagnostics() {
        return developmentAgent.getCompilationDiagnostics();
    }

    private String executeGitCommand(Map<String, Object> params) {
        String operation = (String) params.get("operation");
        String parameters = (String) params.get("parameters");
        return developmentAgent.executeGitCommand(operation, parameters);
    }

    private String executeBuildProject(Map<String, Object> params) {
        String operation = (String) params.get("operation");
        return developmentAgent.buildProject(operation);
    }

    private String executeAnalyzeCode(Map<String, Object> params) {
        String request = (String) params.get("request");
        String scope = (String) params.get("scope");
        return developmentAgent.analyzeCode(request, scope);
    }

    /**
     * Parse une réponse en JSON structuré
     */
    private StructuredAgentResponse parseStructuredResponse(String responseText) {
        try {
            // Extraire le JSON de la réponse (peut être entouré de texte)
            String jsonContent = extractJsonFromResponse(responseText);
            return objectMapper.readValue(jsonContent, StructuredAgentResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse structured response as JSON: {}", e.getMessage());
            log.debug("Response text: {}", responseText);
            return null;
        }
    }

    /**
     * Extrait le JSON d'une réponse qui peut contenir du texte supplémentaire
     */
    private String extractJsonFromResponse(String responseText) {
        // Chercher le JSON entre ``` ou directement
        if (responseText.contains("```json")) {
            int start = responseText.indexOf("```json") + 7;
            int end = responseText.indexOf("```", start);
            if (end > start) {
                return responseText.substring(start, end).trim();
            }
        }

        // Chercher des accolades JSON
        int start = responseText.indexOf('{');
        int end = responseText.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return responseText.substring(start, end + 1);
        }

        return responseText; // Retourner tel quel et laisser le parser décider
    }

    /**
     * Construit le prompt ReAct structuré
     */
    private String buildStructuredReActPrompt(String userRequest, String previousObservation, int iteration) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("""
                You are an expert IntelliJ IDEA development assistant using the ReAct pattern.
                
                You must respond with VALID JSON in this exact format:
                
                ```json
                {
                  "thinking": "Your reasoning about what to do next",
                  "action": {
                    "tool": "toolName",
                    "parameters": {
                      "param1": "value1",
                      "param2": "value2"
                    },
                    "reasoning": "Why you're using this tool"
                  },
                  "observation": "",
                  "final_answer": "",
                  "continue_cycle": true
                }
                ```
                
                Available tools:
                - createJavaClass: {className, filePath, content}
                - createFile: {filePath, content}
                - compileAndCheckErrors: {} (no parameters)
                - getCompilationDiagnostics: {} (no parameters)
                - executeGitCommand: {operation, parameters}
                - buildProject: {operation}
                - analyzeCode: {request, scope}
                
                CRITICAL ReAct Rules:
                1. After creating/modifying code, ALWAYS use compileAndCheckErrors
                2. If compilation fails, use getCompilationDiagnostics to see errors
                3. Fix errors by creating/modifying files with correct imports/syntax
                4. Continue until compilation succeeds
                5. Set "continue_cycle": false and provide "final_answer" when done
                
                """);

        if (iteration == 0) {
            prompt.append("USER REQUEST: ").append(userRequest);
        } else {
            prompt.append("PREVIOUS OBSERVATION: ").append(previousObservation);
            prompt.append("\nCONTINUE ReAct cycle for: ").append(userRequest);
        }

        prompt.append("\n\nRespond with JSON only:");

        return prompt.toString();
    }
}