package fr.baretto.ollamassist.core.agent;

import com.intellij.openapi.project.Project;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import fr.baretto.ollamassist.chat.rag.tools.WebSearchTool;
import fr.baretto.ollamassist.core.agent.execution.ExecutionEngine;
import fr.baretto.ollamassist.core.agent.task.Task;
import fr.baretto.ollamassist.core.agent.task.TaskResult;
import fr.baretto.ollamassist.core.agent.validation.ValidationInterceptor;
import fr.baretto.ollamassist.core.agent.validation.ValidationResult;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent de développement IntelliJ utilisant LangChain4J avec vrais tools
 * Remplace progressivement le TaskPlanner custom par une approche agentic moderne
 *
 * NEW: Automatic validation with ValidationInterceptor
 * - Code changes are automatically validated with compilation checks
 * - Validation runs asynchronously to reduce latency
 * - Detailed error feedback provided for fixing
 */
@Slf4j
public class IntelliJDevelopmentAgent {

    private final Project project;
    private final ExecutionEngine executionEngine;
    private final WebSearchTool webSearchTool;
    private final ValidationInterceptor validationInterceptor;

    public IntelliJDevelopmentAgent(Project project) {
        this.project = project;
        this.executionEngine = new ExecutionEngine(project);
        this.webSearchTool = new WebSearchTool();
        this.validationInterceptor = new ValidationInterceptor(project);
    }

    /**
     * FIX: Auto-correct Java file path based on package declaration
     * Prevents files from being created at project root instead of src/main/java/package/
     */
    private String correctJavaFilePath(String className, String filePath, String classContent) {
        // Extract package from class content
        Pattern packagePattern = Pattern.compile("package\\s+([a-zA-Z0-9_.]+)\\s*;");
        Matcher matcher = packagePattern.matcher(classContent);

        String packageName = null;
        if (matcher.find()) {
            packageName = matcher.group(1);
            log.debug("Extracted package: {}", packageName);
        }

        // If path doesn't start with src/, auto-correct it
        if (!filePath.startsWith("src/")) {
            if (packageName != null) {
                // Build proper Java path: src/main/java/com/example/ClassName.java
                String packagePath = packageName.replace('.', '/');
                String correctedPath = "src/main/java/" + packagePath + "/" + className + ".java";
                log.warn("Auto-correcting Java file path: '{}' -> '{}'", filePath, correctedPath);
                return correctedPath;
            } else {
                // No package, default to src/main/java/
                String correctedPath = "src/main/java/" + className + ".java";
                log.warn("Auto-correcting Java file path (no package): '{}' -> '{}'", filePath, correctedPath);
                return correctedPath;
            }
        }

        // Path already correct
        return filePath;
    }

    @Tool("Create a new Java class file with the specified content")
    public String createJavaClass(
            @P("The name of the class to create") String className,
            @P("The file path where to create the class (e.g., 'src/main/java/com/example/MyClass.java')") String filePath,
            @P("The complete Java code content for the class") String classContent) {

        log.error("TOOL CALLED: createJavaClass");
        log.error("Parameters: className='{}', filePath='{}', contentLength={}",
                className, filePath, classContent != null ? classContent.length() : 0);

        // FIX: Auto-correct path if agent provided wrong location
        String correctedPath = correctJavaFilePath(className, filePath, classContent);
        log.info("Creating Java class: {} at path: {}", className, correctedPath);

        try {
            // Créer une tâche FILE_OPERATION pour utiliser l'ExecutionEngine existant
            Task createFileTask = Task.builder()
                    .id(UUID.randomUUID().toString())
                    .description("Create Java class: " + className)
                    .type(Task.TaskType.FILE_OPERATION)
                    .priority(Task.TaskPriority.NORMAL)
                    .parameters(createFileOperationParams(correctedPath, classContent))
                    .createdAt(LocalDateTime.now())
                    .build();

            TaskResult result = executionEngine.executeTask(createFileTask);

            if (result.isSuccess()) {
                String successMessage = String.format("Successfully created Java class '%s' at '%s'", className, correctedPath);
                log.info(successMessage);

                // AUTOMATIC VALIDATION: Check compilation after creating Java class
                if (validationInterceptor.requiresCompilationCheck("createJavaClass", result)) {
                    log.info("Auto-validating compilation for {}", className);
                    ValidationResult validation = validationInterceptor.autoValidate("createJavaClass", result);

                    if (!validation.isSuccess()) {
                        // Return formatted feedback with errors
                        return validationInterceptor.formatValidationFeedback(validation, successMessage);
                    }
                }

                return successMessage;
            } else {
                String errorMessage = String.format("Failed to create Java class '%s': %s", className, result.getErrorMessage());
                log.error(errorMessage);
                return errorMessage;
            }

        } catch (Exception e) {
            String errorMessage = String.format("Error creating Java class '%s': %s", className, e.getMessage());
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Tool("Create any file with custom content")
    public String createFile(
            @P("The full file path where to create the file") String filePath,
            @P("The content to write to the file") String content) {

        log.error("TOOL CALLED: createFile");
        log.error("Parameters: filePath='{}', contentLength={}",
                filePath, content != null ? content.length() : 0);
        log.info("Creating file: {}", filePath);

        try {
            Task createFileTask = Task.builder()
                    .id(UUID.randomUUID().toString())
                    .description("Create file: " + filePath)
                    .type(Task.TaskType.FILE_OPERATION)
                    .priority(Task.TaskPriority.NORMAL)
                    .parameters(createFileOperationParams(filePath, content))
                    .createdAt(LocalDateTime.now())
                    .build();

            TaskResult result = executionEngine.executeTask(createFileTask);

            if (result.isSuccess()) {
                String successMessage = String.format("Successfully created file '%s'", filePath);
                log.info(successMessage);

                // AUTOMATIC VALIDATION: Check compilation if it's a Java file
                if (validationInterceptor.requiresCompilationCheck("createFile", result)) {
                    log.info("Auto-validating compilation for {}", filePath);
                    ValidationResult validation = validationInterceptor.autoValidate("createFile", result);

                    if (!validation.isSuccess()) {
                        return validationInterceptor.formatValidationFeedback(validation, successMessage);
                    }
                }

                return successMessage;
            } else {
                String errorMessage = String.format("Failed to create file '%s': %s", filePath, result.getErrorMessage());
                log.error(errorMessage);
                return errorMessage;
            }

        } catch (Exception e) {
            String errorMessage = String.format("Error creating file '%s': %s", filePath, e.getMessage());
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Tool("Analyze code in the project")
    public String analyzeCode(
            @P("Description of what to analyze in the code") String analysisRequest,
            @P("Scope of analysis: 'file', 'package', or 'project'") String scope) {

        log.info("Analyzing code: {} (scope: {})", analysisRequest, scope);

        try {
            Task analysisTask = Task.builder()
                    .id(UUID.randomUUID().toString())
                    .description("Code analysis: " + analysisRequest)
                    .type(Task.TaskType.CODE_ANALYSIS)
                    .priority(Task.TaskPriority.NORMAL)
                    .parameters(createAnalysisParams(analysisRequest, scope))
                    .createdAt(LocalDateTime.now())
                    .build();

            TaskResult result = executionEngine.executeTask(analysisTask);

            if (result.isSuccess()) {
                String successMessage = String.format("Code analysis completed: %s", result.getMessage());
                log.info(successMessage);
                return successMessage;
            } else {
                String errorMessage = String.format("Code analysis failed: %s", result.getErrorMessage());
                log.error(errorMessage);
                return errorMessage;
            }

        } catch (Exception e) {
            String errorMessage = String.format("Error during code analysis: %s", e.getMessage());
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Tool("Execute a Git command")
    public String executeGitCommand(
            @P("The Git operation to perform: 'commit', 'push', 'pull', 'status', etc.") String operation,
            @P("Additional parameters for the Git command") String parameters) {

        log.info("Executing Git command: {} with params: {}", operation, parameters);

        try {
            Task gitTask = Task.builder()
                    .id(UUID.randomUUID().toString())
                    .description("Git operation: " + operation)
                    .type(Task.TaskType.GIT_OPERATION)
                    .priority(Task.TaskPriority.NORMAL)
                    .parameters(createGitParams(operation, parameters))
                    .createdAt(LocalDateTime.now())
                    .build();

            TaskResult result = executionEngine.executeTask(gitTask);

            if (result.isSuccess()) {
                String successMessage = String.format("Git command '%s' executed successfully: %s", operation, result.getMessage());
                log.info(successMessage);
                return successMessage;
            } else {
                String errorMessage = String.format("Git command '%s' failed: %s", operation, result.getErrorMessage());
                log.error(errorMessage);
                return errorMessage;
            }

        } catch (Exception e) {
            String errorMessage = String.format("Error executing Git command '%s': %s", operation, e.getMessage());
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Tool("Build or test the project")
    public String buildProject(
            @P("Build operation: 'compile', 'test', 'package', 'clean'") String buildOperation) {

        log.info("Executing build operation: {}", buildOperation);

        try {
            Task buildTask = Task.builder()
                    .id(UUID.randomUUID().toString())
                    .description("Build operation: " + buildOperation)
                    .type(Task.TaskType.BUILD_OPERATION)
                    .priority(Task.TaskPriority.HIGH)
                    .parameters(createBuildParams(buildOperation))
                    .createdAt(LocalDateTime.now())
                    .build();

            TaskResult result = executionEngine.executeTask(buildTask);

            if (result.isSuccess()) {
                String successMessage = String.format("Build operation '%s' completed successfully: %s", buildOperation, result.getMessage());
                log.info(successMessage);
                return successMessage;
            } else {
                String errorMessage = String.format("Build operation '%s' failed: %s", buildOperation, result.getErrorMessage());
                log.error(errorMessage);
                return errorMessage;
            }

        } catch (Exception e) {
            String errorMessage = String.format("Error during build operation '%s': %s", buildOperation, e.getMessage());
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Tool("Search the web for information using DuckDuckGo")
    public String searchWeb(
            @P("The search query to execute") String query) {

        log.info("Executing web search: {}", query);

        try {
            String result = webSearchTool.searchOnDuckDuckGo(query);
            String successMessage = String.format("Web search completed for query: '%s'", query);
            log.info(successMessage);
            return result;
        } catch (Exception e) {
            String errorMessage = String.format("Error during web search for '%s': %s", query, e.getMessage());
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Tool("Compile the project and check for compilation errors")
    public String compileAndCheckErrors() {
        log.error("TOOL CALLED: compileAndCheckErrors");
        log.info("Compiling project and checking for errors");

        try {
            // Utiliser l'outil de build pour compiler
            Task compileTask = Task.builder()
                    .id(UUID.randomUUID().toString())
                    .description("Compile project and check errors")
                    .type(Task.TaskType.BUILD_OPERATION)
                    .priority(Task.TaskPriority.HIGH)
                    .parameters(createBuildParams("compile"))
                    .createdAt(LocalDateTime.now())
                    .build();

            TaskResult result = executionEngine.executeTask(compileTask);

            if (result.isSuccess()) {
                String successMessage = "Project compiled successfully - no errors detected";
                log.info(successMessage);
                return successMessage;
            } else {
                String errorMessage = String.format("Compilation failed with errors: %s", result.getErrorMessage());
                log.error(errorMessage);
                return errorMessage;
            }

        } catch (Exception e) {
            String errorMessage = String.format("Error during compilation check: %s", e.getMessage());
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Tool("Get project compilation diagnostics and errors")
    public String getCompilationDiagnostics() {
        log.error("TOOL CALLED: getCompilationDiagnostics");
        log.info("Getting compilation diagnostics");

        try {
            // Utiliser l'outil de build pour obtenir les diagnostics
            Task diagnosticsTask = Task.builder()
                    .id(UUID.randomUUID().toString())
                    .description("Get compilation diagnostics")
                    .type(Task.TaskType.BUILD_OPERATION)
                    .priority(Task.TaskPriority.HIGH)
                    .parameters(createBuildParams("diagnostics"))
                    .createdAt(LocalDateTime.now())
                    .build();

            TaskResult result = executionEngine.executeTask(diagnosticsTask);

            if (result.isSuccess()) {
                String message = result.getMessage() != null ? result.getMessage() : "No compilation issues found";
                log.info("Diagnostics retrieved successfully");
                return "Compilation diagnostics:\n" + message;
            } else {
                String errorMessage = String.format("Failed to get diagnostics: %s", result.getErrorMessage());
                log.error(errorMessage);
                return errorMessage;
            }

        } catch (Exception e) {
            String errorMessage = String.format("Error getting diagnostics: %s", e.getMessage());
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    // Helper methods pour créer les paramètres des tâches

    private Map<String, Object> createFileOperationParams(String filePath, String content) {
        Map<String, Object> params = new HashMap<>();
        params.put("operation", "create");
        params.put("filePath", filePath);
        params.put("content", content);
        return params;
    }

    private Map<String, Object> createAnalysisParams(String request, String scope) {
        Map<String, Object> params = new HashMap<>();
        params.put("request", request);
        params.put("scope", scope != null ? scope : "project");
        return params;
    }

    private Map<String, Object> createGitParams(String operation, String parameters) {
        Map<String, Object> params = new HashMap<>();
        params.put("operation", operation);

        // Adapter les paramètres selon l'opération Git
        if ("commit".equalsIgnoreCase(operation)) {
            // Pour les commits, GitOperationExecutor attend un paramètre "message"
            params.put("message", parameters != null ? parameters : "Test commit message");
        } else if ("add".equalsIgnoreCase(operation)) {
            // Pour les add, GitOperationExecutor peut accepter une liste de fichiers
            if (parameters != null && !parameters.trim().isEmpty()) {
                // Si des fichiers spécifiques sont mentionnés, les parser
                String[] files = parameters.split(",");
                java.util.List<String> fileList = new java.util.ArrayList<>();
                for (String file : files) {
                    fileList.add(file.trim());
                }
                params.put("files", fileList);
            }
            // Sinon, GitOperationExecutor ajoutera tous les fichiers modifiés
        } else {
            // Pour les autres opérations (push, pull, status), pas de paramètres spéciaux requis
            params.put("parameters", parameters != null ? parameters : "");
        }

        return params;
    }

    private Map<String, Object> createBuildParams(String buildOperation) {
        Map<String, Object> params = new HashMap<>();
        params.put("operation", buildOperation);
        return params;
    }

    /**
     * Obtenir les statistiques de l'agent
     */
    public AgentStats getStats() {
        return AgentStats.builder()
                .activeTasksCount(0)
                .currentState(AgentCoordinator.AgentState.IDLE)
                .totalTasksExecuted(0)
                .successRate(1.0)
                .averageExecutionTime(0)
                .lastActivityTimestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Vérifier si l'agent est disponible
     */
    public boolean isAvailable() {
        return project != null && executionEngine != null;
    }
}