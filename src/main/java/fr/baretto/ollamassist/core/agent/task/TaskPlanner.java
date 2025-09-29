package fr.baretto.ollamassist.core.agent.task;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.model.CompletionRequest;
import fr.baretto.ollamassist.core.service.ModelAssistantService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Planificateur de tâches - Analyse les demandes utilisateur et les décompose en tâches exécutables
 */
@Slf4j
public class TaskPlanner {

    private final Project project;
    private final Map<String, TaskTemplate> taskTemplates;
    private final ModelAssistantService modelAssistantService;

    public TaskPlanner(Project project) {
        this.project = project;
        this.taskTemplates = initializeTaskTemplates();
        this.modelAssistantService = ApplicationManager.getApplication().getService(ModelAssistantService.class);
    }

    /**
     * Planifie les tâches pour une demande utilisateur en utilisant Ollama
     */
    public List<Task> planTasks(String userRequest) {
        log.debug("Planning tasks for request using Ollama: {}", userRequest);

        try {
            // Analyser la demande avec Ollama pour comprendre l'intention
            String analysisPrompt = createTaskAnalysisPrompt(userRequest);
            log.debug("Sending analysis prompt to Ollama: {}", analysisPrompt);

            // Créer une requête de completion
            CompletionRequest completionRequest = CompletionRequest.builder()
                    .context(analysisPrompt)
                    .fileExtension("json") // Indiquer qu'on attend du JSON
                    .build();

            CompletableFuture<String> analysisResult = modelAssistantService.complete(completionRequest);
            String taskAnalysis = analysisResult.join(); // Bloquer pour obtenir le résultat

            log.info("Received task analysis from Ollama: {}", taskAnalysis);
            log.info("Task analysis length: {} characters", taskAnalysis.length());

            // Parser l'analyse et créer les tâches
            List<Task> tasks = parseTaskAnalysisAndCreateTasks(userRequest, taskAnalysis);

            if (tasks.isEmpty()) {
                log.warn("No tasks could be planned from analysis, creating fallback task");
                tasks.add(createGenericTask(userRequest));
            }

            log.info("Planned {} tasks for request using Ollama analysis", tasks.size());
            return tasks;

        } catch (Exception e) {
            log.error("Error using Ollama for task planning, falling back to simple analysis", e);
            return planTasksWithSimpleAnalysis(userRequest);
        }
    }

    /**
     * Méthode de fallback avec analyse simple si Ollama échoue
     */
    private List<Task> planTasksWithSimpleAnalysis(String userRequest) {
        log.debug("Using simple analysis for request: {}", userRequest);

        List<Task> tasks = new ArrayList<>();
        String normalizedRequest = userRequest.toLowerCase().trim();

        // Détection de patterns de demandes courantes
        if (containsKeywords(normalizedRequest, "analyze", "analyse", "check", "vérifier")) {
            tasks.add(createAnalysisTask(userRequest));
        }

        if (containsKeywords(normalizedRequest, "refactor", "refactoring", "améliorer", "optimiser")) {
            tasks.add(createRefactoringTask(userRequest));
        }

        if (containsKeywords(normalizedRequest, "test", "tests", "junit")) {
            tasks.add(createTestGenerationTask(userRequest));
        }

        if (containsKeywords(normalizedRequest, "documentation", "doc", "comment", "javadoc")) {
            tasks.add(createDocumentationTask(userRequest));
        }

        if (containsKeywords(normalizedRequest, "fix", "bug", "error", "problème", "corriger")) {
            tasks.add(createBugFixTask(userRequest));
        }

        if (tasks.isEmpty()) {
            tasks.add(createGenericTask(userRequest));
        }

        log.info("Planned {} tasks with simple analysis", tasks.size());
        return tasks;
    }

    private boolean containsKeywords(String text, String... keywords) {
        return Arrays.stream(keywords).anyMatch(text::contains);
    }

    private Task createAnalysisTask(String request) {
        return Task.builder()
                .id(UUID.randomUUID().toString())
                .description("Analyse du code selon la demande: " + request)
                .type(Task.TaskType.CODE_ANALYSIS)
                .priority(Task.TaskPriority.NORMAL)
                .parameters(Map.of(
                        "request", request,
                        "scope", "project"
                ))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Task createRefactoringTask(String request) {
        return Task.builder()
                .id(UUID.randomUUID().toString())
                .description("Refactoring du code: " + request)
                .type(Task.TaskType.CODE_MODIFICATION)
                .priority(Task.TaskPriority.HIGH)
                .parameters(Map.of(
                        "request", request,
                        "backup", true
                ))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Task createTestGenerationTask(String request) {
        return Task.builder()
                .id(UUID.randomUUID().toString())
                .description("Génération de tests: " + request)
                .type(Task.TaskType.CODE_MODIFICATION)
                .priority(Task.TaskPriority.NORMAL)
                .parameters(Map.of(
                        "request", request,
                        "testType", "unit",
                        "framework", "junit"
                ))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Task createDocumentationTask(String request) {
        return Task.builder()
                .id(UUID.randomUUID().toString())
                .description("Génération de documentation: " + request)
                .type(Task.TaskType.CODE_MODIFICATION)
                .priority(Task.TaskPriority.LOW)
                .parameters(Map.of(
                        "request", request,
                        "format", "javadoc"
                ))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Task createBugFixTask(String request) {
        return Task.builder()
                .id(UUID.randomUUID().toString())
                .description("Correction de bug: " + request)
                .type(Task.TaskType.CODE_MODIFICATION)
                .priority(Task.TaskPriority.CRITICAL)
                .parameters(Map.of(
                        "request", request,
                        "backup", true,
                        "validate", true
                ))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Task createGenericTask(String request) {
        return Task.builder()
                .id(UUID.randomUUID().toString())
                .description("Traitement de la demande: " + request)
                .type(Task.TaskType.COMPOSITE)
                .priority(Task.TaskPriority.NORMAL)
                .parameters(Map.of("request", request))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Map<String, TaskTemplate> initializeTaskTemplates() {
        Map<String, TaskTemplate> templates = new HashMap<>();

        // TODO: Implémenter un système de templates plus sophistiqué
        templates.put("code_analysis", TaskTemplate.builder()
                .name("Code Analysis")
                .description("Analyser le code du projet")
                .type(Task.TaskType.CODE_ANALYSIS)
                .defaultPriority(Task.TaskPriority.NORMAL)
                .build());

        return templates;
    }

    /**
     * Crée un prompt pour analyser la demande utilisateur avec Ollama
     */
    private String createTaskAnalysisPrompt(String userRequest) {
        return String.format("""
                En tant qu'assistant de développement, analysez cette demande utilisateur et proposez des tâches concrètes à exécuter.
                
                DEMANDE UTILISATEUR: "%s"
                
                TYPES DE TÂCHES DISPONIBLES:
                - CODE_ANALYSIS: Analyser le code existant (lecture seule)
                - CODE_MODIFICATION: Modifier du code existant (non implémenté - évitez)
                - FILE_OPERATION: RECOMMANDÉ pour créer/modifier/supprimer des fichiers
                - BUILD_OPERATION: Compiler, tester, construire le projet
                - GIT_OPERATION: Opérations Git (commit, push, etc.)
                - MCP_OPERATION: Opérations avec des outils externes
                - COMPOSITE: Tâches complexes (non implémenté - évitez)
                
                PARAMÈTRES POUR FILE_OPERATION:
                - operation: "create" (pour créer un fichier)
                - filePath: chemin relatif du fichier (ex: "src/main/java/HelloWorld.java")
                - content: contenu du fichier à créer
                
                PRIORITÉS DISPONIBLES:
                - LOW: Tâches non urgentes
                - NORMAL: Tâches standard
                - HIGH: Tâches importantes
                - CRITICAL: Tâches critiques
                
                RÉPONDEZ AU FORMAT JSON STRICT suivant (un seul objet JSON, pas de texte avant ou après):
                {
                  "tasks": [
                    {
                      "description": "Description claire de la tâche",
                      "type": "TYPE_DE_TACHE",
                      "priority": "PRIORITE",
                      "parameters": {
                        "key1": "value1",
                        "key2": "value2"
                      }
                    }
                  ],
                  "reasoning": "Explication de l'analyse et des tâches choisies"
                }
                
                Exemple pour "créer une classe Calculator":
                {
                  "tasks": [
                    {
                      "description": "Créer la classe Calculator avec les méthodes de base",
                      "type": "FILE_OPERATION",
                      "priority": "NORMAL",
                      "parameters": {
                        "operation": "create",
                        "filePath": "src/main/java/Calculator.java",
                        "content": "public class Calculator {
                    public int add(int a, int b) {
                        return a + b;
                    }
                    \n    public int subtract(int a, int b) {
                        return a - b;
                    }
                }"
                      }
                    }
                  ],
                  "reasoning": "L'utilisateur veut créer une nouvelle classe Calculator. J'utilise FILE_OPERATION avec operation=create pour créer le fichier Java avec le contenu de base de la classe."
                }
                """, userRequest);
    }

    /**
     * Parse l'analyse Ollama et crée les tâches correspondantes
     */
    private List<Task> parseTaskAnalysisAndCreateTasks(String userRequest, String taskAnalysis) {
        List<Task> tasks = new ArrayList<>();

        try {
            // Extraire le JSON de la réponse (en cas de texte supplémentaire)
            String jsonContent = extractJsonFromResponse(taskAnalysis);
            log.info("Extracted JSON: {}", jsonContent);

            // Parser le JSON manuellement (simple parsing)
            Map<String, Object> analysisData = parseJsonResponse(jsonContent);
            log.info("Parsed analysis data: {}", analysisData);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> taskSpecs = (List<Map<String, Object>>) analysisData.get("tasks");

            if (taskSpecs != null) {
                log.info("Found {} task specifications from Ollama", taskSpecs.size());
                for (Map<String, Object> taskSpec : taskSpecs) {
                    log.info("Creating task from spec: {}", taskSpec);
                    Task task = createTaskFromSpec(userRequest, taskSpec);
                    if (task != null) {
                        tasks.add(task);
                        log.info("Created task: {} - {}", task.getType(), task.getDescription());
                    } else {
                        log.warn("Failed to create task from spec: {}", taskSpec);
                    }
                }
            } else {
                log.warn("No task specifications found in analysis data");
            }

            String reasoning = (String) analysisData.get("reasoning");
            if (reasoning != null && !reasoning.trim().isEmpty()) {
                log.info("Ollama reasoning: {}", reasoning);
            }

        } catch (Exception e) {
            log.error("Error parsing task analysis from Ollama: {}", e.getMessage());
            log.debug("Raw analysis content: {}", taskAnalysis);
        }

        return tasks;
    }

    /**
     * Extrait le contenu JSON de la réponse Ollama (peut contenir du texte supplémentaire)
     */
    private String extractJsonFromResponse(String response) {
        // Chercher le premier '{' et le dernier '}'
        int startIndex = response.indexOf('{');
        int endIndex = response.lastIndexOf('}');

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }

        // Si pas de JSON trouvé, retourner la réponse complète
        return response;
    }

    /**
     * Parse simple du JSON (sans dépendance externe)
     */
    private Map<String, Object> parseJsonResponse(String jsonContent) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Parser simple pour extraire les informations essentielles
            // Cette implémentation basique peut être améliorée avec Jackson si nécessaire

            // Extraire le reasoning
            String reasoning = extractJsonString(jsonContent, "reasoning");
            if (reasoning != null) {
                result.put("reasoning", reasoning);
            }

            // Extraire les tâches (parsing basique)
            List<Map<String, Object>> tasks = extractTasksFromJson(jsonContent);
            result.put("tasks", tasks);

        } catch (Exception e) {
            log.error("Error in simple JSON parsing: {}", e.getMessage());
            // Fallback: créer une structure basique
            result.put("tasks", new ArrayList<>());
            result.put("reasoning", "Erreur lors du parsing JSON");
        }

        return result;
    }

    /**
     * Extrait une chaîne de caractères d'un JSON simple
     */
    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*(?:\\\\.[^\"]*)*?)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);

        if (m.find()) {
            return m.group(1).replace("\\\"", "\"").replace("\\n", "\n");
        }

        return null;
    }

    /**
     * Extrait les tâches du JSON de manière basique
     */
    private List<Map<String, Object>> extractTasksFromJson(String json) {
        List<Map<String, Object>> tasks = new ArrayList<>();

        // Trouver le tableau de tâches
        int tasksStart = json.indexOf("\"tasks\"");
        if (tasksStart == -1) return tasks;

        int arrayStart = json.indexOf('[', tasksStart);
        if (arrayStart == -1) return tasks;

        int arrayEnd = findMatchingBracket(json, arrayStart);
        if (arrayEnd == -1) return tasks;

        String tasksArray = json.substring(arrayStart + 1, arrayEnd);

        // Parser chaque tâche (très basique)
        String[] taskObjects = splitTaskObjects(tasksArray);

        for (String taskJson : taskObjects) {
            if (taskJson.trim().isEmpty()) continue;

            Map<String, Object> task = new HashMap<>();

            String description = extractJsonString(taskJson, "description");
            String type = extractJsonString(taskJson, "type");
            String priority = extractJsonString(taskJson, "priority");

            if (description != null) task.put("description", description);
            if (type != null) task.put("type", type);
            if (priority != null) task.put("priority", priority);

            // Extraire les paramètres de base
            Map<String, Object> parameters = extractParametersFromJson(taskJson);
            task.put("parameters", parameters);

            if (!task.isEmpty()) {
                tasks.add(task);
            }
        }

        return tasks;
    }

    /**
     * Trouve le crochet fermant correspondant
     */
    private int findMatchingBracket(String json, int startPos) {
        int count = 1;
        for (int i = startPos + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') count++;
            else if (c == ']') {
                count--;
                if (count == 0) return i;
            }
        }
        return -1;
    }

    /**
     * Divise les objets de tâches (parsing très basique)
     */
    private String[] splitTaskObjects(String tasksArray) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = 0;

        for (int i = 0; i < tasksArray.length(); i++) {
            char c = tasksArray.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    objects.add(tasksArray.substring(start, i + 1));
                }
            }
        }

        return objects.toArray(new String[0]);
    }

    /**
     * Extrait les paramètres d'un objet tâche JSON
     */
    private Map<String, Object> extractParametersFromJson(String taskJson) {
        Map<String, Object> parameters = new HashMap<>();

        int paramsStart = taskJson.indexOf("\"parameters\"");
        if (paramsStart == -1) return parameters;

        int objStart = taskJson.indexOf('{', paramsStart);
        if (objStart == -1) return parameters;

        int objEnd = findMatchingBrace(taskJson, objStart);
        if (objEnd == -1) return parameters;

        String paramsJson = taskJson.substring(objStart + 1, objEnd);

        // Parser les paires clé-valeur basiques
        String[] pairs = paramsJson.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replaceAll("\"", "");
                String value = keyValue[1].trim().replaceAll("\"", "");
                if (!key.isEmpty() && !value.isEmpty()) {
                    parameters.put(key, value);
                }
            }
        }

        return parameters;
    }

    /**
     * Trouve l'accolade fermante correspondante
     */
    private int findMatchingBrace(String json, int startPos) {
        int count = 1;
        for (int i = startPos + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') count++;
            else if (c == '}') {
                count--;
                if (count == 0) return i;
            }
        }
        return -1;
    }

    /**
     * Crée une tâche à partir d'une spécification parsée
     */
    private Task createTaskFromSpec(String userRequest, Map<String, Object> taskSpec) {
        try {
            String description = (String) taskSpec.get("description");
            String typeStr = (String) taskSpec.get("type");
            String priorityStr = (String) taskSpec.get("priority");

            if (description == null || typeStr == null) {
                log.warn("Missing required fields in task spec: {}", taskSpec);
                return null;
            }

            // Convertir le type de tâche
            Task.TaskType taskType;
            try {
                taskType = Task.TaskType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid task type '{}', using COMPOSITE", typeStr);
                taskType = Task.TaskType.COMPOSITE;
            }

            // Convertir la priorité
            Task.TaskPriority priority;
            try {
                priority = priorityStr != null ? Task.TaskPriority.valueOf(priorityStr) : Task.TaskPriority.NORMAL;
            } catch (IllegalArgumentException e) {
                log.warn("Invalid priority '{}', using NORMAL", priorityStr);
                priority = Task.TaskPriority.NORMAL;
            }

            // Préparer les paramètres
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) taskSpec.getOrDefault("parameters", new HashMap<>());

            // Ajouter la demande utilisateur originale (compatibilité avec les executors existants)
            parameters.put("request", userRequest);
            parameters.put("original_request", userRequest);
            parameters.put("llm_analyzed", true);

            return Task.builder()
                    .id(UUID.randomUUID().toString())
                    .description(description)
                    .type(taskType)
                    .priority(priority)
                    .parameters(parameters)
                    .createdAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Error creating task from spec: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Template de tâche pour la planification
     */
    @lombok.Builder
    @lombok.Value
    private static class TaskTemplate {
        String name;
        String description;
        Task.TaskType type;
        Task.TaskPriority defaultPriority;
        Map<String, Object> defaultParameters;
    }
}