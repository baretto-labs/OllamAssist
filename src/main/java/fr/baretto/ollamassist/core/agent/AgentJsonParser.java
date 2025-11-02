package fr.baretto.ollamassist.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Parser for JSON-formatted agent responses
 * Handles parsing and execution of tool actions from legacy JSON format
 */
@Slf4j
public class AgentJsonParser {

    /**
     * System prompt for JSON-based agent mode (legacy fallback)
     */
    public static final String PROMPT_SYSTEM = """
            You are an IntelliJ IDEA development assistant agent. When the user asks you to perform development tasks,
            respond with JSON commands that specify exactly what actions to take.

            Available tools:
            - createJavaClass: Create Java classes with proper package structure
            - createFile: Create any type of file with custom content
            - analyzeCode: Analyze existing code in the project
            - executeGitCommand: Execute Git operations (commit, push, pull, status, etc.)
            - buildProject: Build, test, or package the project

            RESPONSE FORMAT: You must respond with JSON in this exact format:
            ```json
            {
              "actions": [
                {
                  "tool": "createFile",
                  "parameters": {
                    "filePath": "relative/path/to/file.java",
                    "content": "file content here"
                  }
                }
              ],
              "message": "Description en français de ce qui a été fait"
            }
            ```

            Example 1 - Creating a HelloWorld class in default package:
            ```json
            {
              "actions": [
                {
                  "tool": "createJavaClass",
                  "parameters": {
                    "className": "HelloWorld",
                    "filePath": "src/main/java/HelloWorld.java",
                    "classContent": "public class HelloWorld {\\n    public void sayHello() {\\n        System.out.println(\\"Hello World!\\");\\n    }\\n}"
                  }
                }
              ],
              "message": "Classe HelloWorld créée avec succès dans src/main/java/"
            }
            ```

            Example 2 - Creating a class in a package (IMPORTANT: use proper structure):
            ```json
            {
              "actions": [
                {
                  "tool": "createJavaClass",
                  "parameters": {
                    "className": "UserService",
                    "filePath": "src/main/java/com/example/service/UserService.java",
                    "classContent": "package com.example.service;\\n\\npublic class UserService {\\n    public void createUser() {\\n        // TODO\\n    }\\n}"
                  }
                }
              ],
              "message": "Classe UserService créée dans le package com.example.service"
            }
            ```

            IMPORTANT JAVA PROJECT STRUCTURE:
            - Java classes MUST be placed in: src/main/java/[package_path]/ClassName.java
            - If class has "package com.example.service;", path MUST be: src/main/java/com/example/service/ClassName.java
            - Never create Java files at project root (e.g., "Test.java" is WRONG)
            - Always include proper Java package declarations in classContent
            - Always respond in French for the message, but write code in English
            - Always return valid JSON - no additional text before or after
            """;

    private final IntelliJDevelopmentAgent developmentAgent;

    public AgentJsonParser(IntelliJDevelopmentAgent developmentAgent) {
        this.developmentAgent = developmentAgent;
    }

    /**
     * Parse le JSON retourné par Ollama et exécute les actions correspondantes
     *
     * @param jsonResponse réponse JSON du modèle
     * @return résultat de l'exécution des actions
     */
    public String parseAndExecuteActions(String jsonResponse) {
        try {
            log.debug("Parsing JSON response from agent");

            // Extraire le JSON si c'est dans des ```json
            String cleanJson = extractJsonFromResponse(jsonResponse);
            log.debug("Extracted clean JSON, length: {}", cleanJson.length());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(cleanJson);

            StringBuilder resultBuilder = new StringBuilder();
            JsonNode actionsNode = root.get("actions");
            JsonNode messageNode = root.get("message");

            if (actionsNode != null && actionsNode.isArray()) {
                for (JsonNode actionNode : actionsNode) {
                    String tool = actionNode.get("tool").asText();
                    JsonNode parametersNode = actionNode.get("parameters");

                    log.debug("Executing tool: {} with {} parameter(s)", tool, parametersNode.size());

                    String toolResult = executeToolAction(tool, parametersNode);
                    resultBuilder.append(toolResult).append("\n");
                }
            }

            // Ajouter le message de l'agent
            if (messageNode != null) {
                resultBuilder.append("\n").append(messageNode.asText());
            }

            return resultBuilder.toString();

        } catch (Exception e) {
            log.error("JSON parsing error", e);
            return "Erreur lors de l'exécution: " + e.getMessage() + "\n\nRéponse brute: " + jsonResponse;
        }
    }

    /**
     * Extrait le JSON de la réponse (retire les ```json si présents)
     *
     * @param response réponse brute du modèle
     * @return JSON nettoyé
     */
    private String extractJsonFromResponse(String response) {
        String trimmed = response.trim();

        // Chercher ```json même si pas au début
        if (trimmed.contains("```json")) {
            int startIndex = trimmed.indexOf("```json") + 7;
            int endIndex = trimmed.indexOf("```", startIndex);
            if (endIndex > startIndex) {
                return trimmed.substring(startIndex, endIndex).trim();
            }
        }

        // Chercher ``` simple même si pas au début
        if (trimmed.contains("```")) {
            int startIndex = trimmed.indexOf("```") + 3;
            int endIndex = trimmed.indexOf("```", startIndex);
            if (endIndex > startIndex) {
                return trimmed.substring(startIndex, endIndex).trim();
            }
        }

        // Chercher le premier { et le dernier } (JSON brut)
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }

        return trimmed;
    }

    /**
     * Exécute une action tool spécifique via l'agent de développement
     *
     * @param tool       nom du tool à exécuter
     * @param parameters paramètres du tool
     * @return résultat de l'exécution
     */
    private String executeToolAction(String tool, JsonNode parameters) {
        log.debug("Executing tool action: {}", tool);

        try {
            switch (tool) {
                case "createJavaClass":
                    return developmentAgent.createJavaClass(
                            parameters.get("className").asText(),
                            parameters.get("filePath").asText(),
                            parameters.get("classContent").asText()
                    );

                case "createFile":
                    return developmentAgent.createFile(
                            parameters.get("filePath").asText(),
                            parameters.get("content").asText()
                    );

                case "analyzeCode":
                    return developmentAgent.analyzeCode(
                            parameters.get("request").asText(),
                            parameters.has("scope") ? parameters.get("scope").asText() : "project"
                    );

                case "executeGitCommand":
                    return developmentAgent.executeGitCommand(
                            parameters.get("operation").asText(),
                            parameters.has("parameters") ? parameters.get("parameters").asText() : ""
                    );

                case "buildProject":
                    return developmentAgent.buildProject(
                            parameters.get("operation").asText()
                    );

                default:
                    return "Tool inconnu: " + tool;
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'exécution du tool {}: {}", tool, e.getMessage(), e);
            return "Erreur lors de l'exécution du tool " + tool + ": " + e.getMessage();
        }
    }
}
