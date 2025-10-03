package fr.baretto.ollamassist.core.agent.react;

import com.intellij.openapi.project.Project;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.core.agent.IntelliJDevelopmentAgent;
import fr.baretto.ollamassist.core.agent.StructuredAgentResponse;
import fr.baretto.ollamassist.core.agent.validation.ValidationInterceptor;
import fr.baretto.ollamassist.core.agent.validation.ValidationResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Explicit ReAct loop controller
 * Manages Think-Act-Observe-Fix cycles programmatically
 */
@Slf4j
public class ReActLoopController {

    private static final int MAX_ITERATIONS = 10;
    private static final int TIMEOUT_SECONDS = 120;

    private final Project project;
    private final IntelliJDevelopmentAgent agent;
    private final OllamaService ollamaService;
    private final ValidationInterceptor validationInterceptor;

    public ReActLoopController(Project project,
                               IntelliJDevelopmentAgent agent,
                               OllamaService ollamaService) {
        this.project = project;
        this.agent = agent;
        this.ollamaService = ollamaService;
        this.validationInterceptor = new ValidationInterceptor(project);
    }

    /**
     * Executes a user request with explicit ReAct loop
     */
    public CompletableFuture<ReActResult> executeWithLoop(String userRequest) {
        log.info("🔄 Starting ReAct loop for: {}", userRequest);

        ReActContext context = new ReActContext(userRequest, project);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return runReActCycle(context);
            } catch (Exception e) {
                log.error("Error in ReAct loop execution", e);
                return ReActResult.error(context, "ReAct loop error: " + e.getMessage());
            }
        });
    }

    /**
     * Runs the complete ReAct cycle
     */
    private ReActResult runReActCycle(ReActContext context) {
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            context.incrementIteration();
            log.info("🔄 ReAct iteration {}/{}", iteration + 1, MAX_ITERATIONS);

            // 1. THINK: Ask model to reason
            ThinkingResult thinking = askModelToThink(context);
            if (thinking == null) {
                return ReActResult.error(context, "Failed to get thinking from model");
            }

            context.addThinking(new ReActContext.ThinkingStep(
                    thinking.getReasoning(),
                    thinking.hasFinalAnswer(),
                    thinking.getFinalAnswer()
            ));

            // 2. Check if model has final answer
            if (thinking.hasFinalAnswer()) {
                log.info("✅ Model provided final answer");
                return ReActResult.success(context, thinking.getFinalAnswer());
            }

            // 3. ACT: Execute the proposed action
            if (!thinking.hasAction()) {
                log.warn("No action proposed by model");
                return ReActResult.error(context, "Model did not propose any action");
            }

            ActionResult actionResult = executeAction(thinking, context);
            context.addAction(new ReActContext.ActionStep(
                    thinking.getToolName(),
                    thinking.getActionDescription(),
                    thinking.getParameters()
            ));

            // 4. OBSERVE: Verify the result (with automatic validation)
            ObservationResult observation = observeResult(actionResult, thinking.getToolName(), context);
            context.addObservation(new ReActContext.ObservationStep(
                    observation.isSuccess(),
                    observation.getMessage(),
                    observation.getErrors(),
                    observation.getWarnings()
            ));

            // 5. DECIDE: Should we terminate?
            if (shouldTerminate(context, observation)) {
                log.info("✅ ReAct cycle completed successfully");
                return ReActResult.success(context, observation.getMessage());
            }

            // 6. FIX: Prepare for next iteration if errors detected
            if (observation.hasErrors()) {
                context.prepareFixIteration(observation.getErrors());
                log.info("🔧 Preparing fix iteration for {} errors", observation.getErrors().size());
            }
        }

        // Max iterations reached
        log.warn("⚠️ Max iterations reached without completion");
        return ReActResult.maxIterationsReached(context);
    }

    /**
     * Asks the model to think about the next step
     */
    private ThinkingResult askModelToThink(ReActContext context) {
        try {
            String thinkingPrompt = buildThinkingPrompt(context);
            log.debug("Asking model to think with prompt length: {}", thinkingPrompt.length());

            // Get response from model
            CompletableFuture<Response<AiMessage>> future = new CompletableFuture<>();

            ollamaService.getAssistant().chat(thinkingPrompt)
                    .onCompleteResponse(future::complete)
                    .onError(future::completeExceptionally)
                    .start();

            Response<AiMessage> response = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String responseText = response.content().text();

            log.debug("Model response received: {} chars", responseText.length());

            // Parse the response
            return parseThinkingResponse(responseText);

        } catch (Exception e) {
            log.error("Error asking model to think", e);
            return null;
        }
    }

    /**
     * Executes an action proposed by the model
     */
    private ActionResult executeAction(ThinkingResult thinking, ReActContext context) {
        String toolName = thinking.getToolName();
        Map<String, Object> params = thinking.getParameters();

        log.info("🔧 Executing action: {}", toolName);

        try {
            String result = executeToolAction(toolName, params);
            return ActionResult.success(result);
        } catch (Exception e) {
            log.error("Error executing action: {}", toolName, e);
            return ActionResult.failure("Action execution error: " + e.getMessage());
        }
    }

    /**
     * Executes a specific tool action
     */
    private String executeToolAction(String toolName, Map<String, Object> params) {
        return switch (toolName.toLowerCase()) {
            case "createjavaclass" -> agent.createJavaClass(
                    (String) params.get("className"),
                    (String) params.get("filePath"),
                    (String) params.get("content")
            );
            case "createfile" -> agent.createFile(
                    (String) params.get("filePath"),
                    (String) params.get("content")
            );
            case "compileandcheckerrors" -> agent.compileAndCheckErrors();
            case "getcompilationdiagnostics" -> agent.getCompilationDiagnostics();
            case "executegitcommand" -> agent.executeGitCommand(
                    (String) params.get("operation"),
                    (String) params.get("parameters")
            );
            case "buildproject" -> agent.buildProject(
                    (String) params.get("operation")
            );
            case "analyzecode" -> agent.analyzeCode(
                    (String) params.get("request"),
                    (String) params.get("scope")
            );
            default -> "❌ Unknown tool: " + toolName;
        };
    }

    /**
     * Observes the result of an action with automatic validation
     */
    private ObservationResult observeResult(ActionResult actionResult, String toolName, ReActContext context) {
        log.debug("👁️ Observing result of: {}", toolName);

        if (!actionResult.isSuccess()) {
            return ObservationResult.failure(
                    actionResult.getErrorMessage(),
                    java.util.List.of(actionResult.getErrorMessage())
            );
        }

        // ✨ AUTOMATIC VALIDATION: Check if action requires compilation check
        if (validationInterceptor.requiresCompilationCheck(toolName, null)) {
            log.info("🔍 Auto-validating compilation after {}", toolName);

            ValidationResult validation = validationInterceptor.autoValidate(
                    toolName,
                    null // TaskResult not needed here
            );

            if (validation.isSuccess()) {
                context.markValidationCompleted();
                return ObservationResult.success(
                        actionResult.getMessage() + "\n✅ Code validated - compilation successful"
                );
            } else {
                // Compilation failed - return errors for fixing
                return ObservationResult.failure(
                        "Action succeeded but compilation failed",
                        validation.getErrors()
                );
            }
        }

        // No validation needed
        return ObservationResult.success(actionResult.getMessage());
    }

    /**
     * Decides if the ReAct cycle should terminate
     */
    private boolean shouldTerminate(ReActContext context, ObservationResult observation) {
        // Terminate if:
        // 1. Last observation was successful
        // 2. Validation has been completed (for code changes)
        // 3. No errors or warnings

        boolean observationSuccess = observation.isSuccess();
        boolean validationComplete = context.isCompletedValidation() ||
                !requiresValidation(context);
        boolean noErrors = !observation.hasErrors();

        log.debug("Termination check: success={}, validation={}, noErrors={}",
                observationSuccess, validationComplete, noErrors);

        return observationSuccess && validationComplete && noErrors;
    }

    /**
     * Checks if the context requires validation
     */
    private boolean requiresValidation(ReActContext context) {
        // If any action was a code modification, validation is required
        return context.getActionSteps().stream()
                .anyMatch(action -> action.getToolName().matches(
                        "createJavaClass|createFile|modifyCode"
                ));
    }

    /**
     * Builds the thinking prompt for the model
     */
    private String buildThinkingPrompt(ReActContext context) {
        StringBuilder prompt = new StringBuilder();

        if (context.getIterationCount() == 1) {
            // First iteration
            prompt.append(buildInitialReActPrompt());
            prompt.append("\n\nUSER REQUEST: ").append(context.getOriginalRequest());
        } else {
            // Continuation iteration
            prompt.append(buildContinuationPrompt(context));
        }

        prompt.append("\n\nRespond with JSON only:");
        return prompt.toString();
    }

    /**
     * Builds the initial ReAct prompt
     */
    private String buildInitialReActPrompt() {
        return """
                You are an expert IntelliJ IDEA development assistant using the ReAct pattern.

                Respond with VALID JSON in this format:

                ```json
                {
                  "thinking": "Your reasoning about what to do next",
                  "action": {
                    "tool": "toolName",
                    "parameters": {"param1": "value1"},
                    "reasoning": "Why you're using this tool"
                  },
                  "final_answer": null,
                  "continue_cycle": true
                }
                ```

                Available tools:
                - createJavaClass: {className, filePath, content}
                - createFile: {filePath, content}
                - compileAndCheckErrors: {} (AUTOMATICALLY CALLED - don't call manually)
                - getCompilationDiagnostics: {}
                - executeGitCommand: {operation, parameters}
                - buildProject: {operation}
                - analyzeCode: {request, scope}

                IMPORTANT: After createJavaClass or createFile, compilation is AUTOMATICALLY checked.
                You will receive compilation results in the next iteration.
                If compilation fails, FIX the errors immediately.

                Set "continue_cycle": false and provide "final_answer" when task is complete.
                """;
    }

    /**
     * Builds the continuation prompt with previous observation
     */
    private String buildContinuationPrompt(ReActContext context) {
        ReActContext.ObservationStep lastObs = context.getLastObservation();

        StringBuilder prompt = new StringBuilder();
        prompt.append("PREVIOUS OBSERVATION:\n");

        if (lastObs != null) {
            prompt.append(lastObs.isSuccess() ? "✅ " : "❌ ");
            prompt.append(lastObs.getResult()).append("\n");

            if (lastObs.hasErrors()) {
                prompt.append("\n🔧 ERRORS TO FIX:\n");
                for (String error : lastObs.getErrors()) {
                    prompt.append("  - ").append(error).append("\n");
                }
            }
        }

        prompt.append("\nCONTINUE ReAct cycle for: ").append(context.getOriginalRequest());
        prompt.append("\n\n").append(buildInitialReActPrompt());

        return prompt.toString();
    }

    /**
     * Parses the thinking response from the model
     */
    private ThinkingResult parseThinkingResponse(String responseText) {
        try {
            // Try to parse as structured response
            StructuredAgentResponse structured = parseStructuredResponse(responseText);

            if (structured != null && structured.hasFinalAnswer()) {
                return ThinkingResult.finalAnswer(
                        structured.getThinking(),
                        structured.getFinalAnswer()
                );
            }

            if (structured != null && structured.hasAction()) {
                StructuredAgentResponse.AgentAction action = structured.getAction();
                return ThinkingResult.action(
                        structured.getThinking(),
                        action.getTool(),
                        action.getParameters(),
                        action.getReasoning()
                );
            }

            return null;
        } catch (Exception e) {
            log.error("Error parsing thinking response", e);
            return null;
        }
    }

    /**
     * Parses structured response from JSON
     */
    private StructuredAgentResponse parseStructuredResponse(String responseText) {
        try {
            String jsonContent = extractJsonFromResponse(responseText);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(jsonContent, StructuredAgentResponse.class);
        } catch (Exception e) {
            log.debug("Failed to parse as structured response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts JSON from response text
     */
    private String extractJsonFromResponse(String responseText) {
        if (responseText.contains("```json")) {
            int start = responseText.indexOf("```json") + 7;
            int end = responseText.indexOf("```", start);
            if (end > start) {
                return responseText.substring(start, end).trim();
            }
        }

        int start = responseText.indexOf('{');
        int end = responseText.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return responseText.substring(start, end + 1);
        }

        return responseText;
    }

    /**
     * Cleans up resources
     */
    public void cleanup() {
        if (validationInterceptor != null) {
            validationInterceptor.cleanup();
        }
    }

    // Helper classes for internal data flow

    private record ThinkingResult(
            String reasoning,
            boolean hasFinalAnswer,
            String finalAnswer,
            boolean hasAction,
            String toolName,
            Map<String, Object> parameters,
            String actionDescription
    ) {
        static ThinkingResult finalAnswer(String reasoning, String answer) {
            return new ThinkingResult(reasoning, true, answer, false, null, null, null);
        }

        static ThinkingResult action(String reasoning, String tool, Map<String, Object> params, String description) {
            return new ThinkingResult(reasoning, false, null, true, tool, params, description);
        }
    }

    private record ActionResult(boolean success, String message, String errorMessage) {
        static ActionResult success(String message) {
            return new ActionResult(true, message, null);
        }

        static ActionResult failure(String errorMessage) {
            return new ActionResult(false, null, errorMessage);
        }
    }

    private record ObservationResult(
            boolean success,
            String message,
            java.util.List<String> errors,
            java.util.List<String> warnings
    ) {
        static ObservationResult success(String message) {
            return new ObservationResult(true, message, null, null);
        }

        static ObservationResult failure(String message, java.util.List<String> errors) {
            return new ObservationResult(false, message, errors, null);
        }

        boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }

        boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }
    }
}
