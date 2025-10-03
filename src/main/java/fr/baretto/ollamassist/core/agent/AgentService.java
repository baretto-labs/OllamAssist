package fr.baretto.ollamassist.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.core.agent.react.ReActLoopController;
import fr.baretto.ollamassist.core.agent.react.ReActResult;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import fr.baretto.ollamassist.setting.agent.AgentModeSettings;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service agent LangChain4J qui remplace l'approche TaskPlanner
 * Utilise les vrais tools LangChain4J avec function calling
 */
@Slf4j
@Service(Service.Level.PROJECT)
public final class AgentService {

    private final Project project;
    private final IntelliJDevelopmentAgent developmentAgent;
    private final OllamaService ollamaService;
    private final OllamaChatModel chatModel;
    private final AgentInterface agentInterface;
    private final boolean useNativeTools;
    private final AgentModeSettings agentSettings;

    public AgentService(Project project) {
        this.project = project;
        this.agentSettings = AgentModeSettings.getInstance();
        this.developmentAgent = new IntelliJDevelopmentAgent(project);

        // Utiliser le service Ollama existant qui a RAG/web/context
        this.ollamaService = project.getService(OllamaService.class);

        // ✨ Check agent model availability
        ModelAvailabilityChecker checker = new ModelAvailabilityChecker();
        ModelAvailabilityChecker.ModelAvailabilityResult availabilityCheck = checker.checkAgentModelAvailability();

        if (!availabilityCheck.isAvailable()) {
            log.error("❌ AGENT MODEL NOT AVAILABLE: {}", availabilityCheck.getUserMessage());
            // Will be handled in executeUserRequest
        } else {
            log.info("✅ Agent model '{}' is available", agentSettings.getAgentModelName());
        }

        // ✨ Use agent-specific model configuration (gpt-oss) instead of completion model
        String agentUrl = agentSettings.getAgentOllamaUrl() != null
                ? agentSettings.getAgentOllamaUrl()
                : OllamAssistSettings.getInstance().getCompletionOllamaUrl();

        String agentModelName = agentSettings.getAgentModelName();

        log.info("🤖 Initializing agent with model: '{}' at {}", agentModelName, agentUrl);

        // Créer le modèle Ollama pour l'agent avec paramètres optimisés pour ReAct
        this.chatModel = OllamaChatModel.builder()
                .temperature(0.2)  // Bas pour raisonnement cohérent
                .topK(30)
                .topP(0.7)
                .baseUrl(agentUrl)
                .modelName(agentModelName)
                .timeout(OllamAssistSettings.getInstance().getTimeoutDuration())
                .build();

        // Activer les tools natifs LangChain4J avec Ollama
        log.info("🔧 Initializing agent with native tools");
        boolean nativeToolsSuccess = false;
        AgentInterface tempInterface = null;

        try {
            tempInterface = AiServices.builder(AgentInterface.class)
                    .chatModel(chatModel)
                    .tools(developmentAgent) // ACTIVATION DES TOOLS NATIFS
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .build();
            log.info("✅ NATIVE TOOLS: Successfully initialized with tools");
            nativeToolsSuccess = true;
        } catch (Exception e) {
            log.error("❌ NATIVE TOOLS: Failed to initialize with tools, falling back to no-tools mode", e);
            // Fallback si les tools ne marchent pas
            tempInterface = AiServices.builder(AgentInterface.class)
                    .chatModel(chatModel)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .build();
        }

        this.agentInterface = tempInterface;
        this.useNativeTools = nativeToolsSuccess;

        log.info("🔧 AGENT ARCHITECTURE: {} mode activated with model '{}'",
                useNativeTools ? "NATIVE TOOLS" : "JSON FALLBACK", agentModelName);
        log.info("AgentService initialized with LangChain4J tools for project: {}", project.getName());
    }

    /**
     * Exécute une requête utilisateur avec streaming unifié intelligent
     */
    public void executeUserRequestWithStreaming(String userRequest) {
        log.error("🚀 DEBUG: UNIFIED STREAMING AGENT: Processing user request: {}", userRequest);

        // ✨ Vérifier si le modèle agent est disponible
        ModelAvailabilityChecker checker = new ModelAvailabilityChecker();
        ModelAvailabilityChecker.ModelAvailabilityResult modelCheck = checker.checkAgentModelAvailability();

        if (!modelCheck.isAvailable()) {
            log.error("❌ Agent model not available: {}", modelCheck.getStatus());

            // ✨ Afficher une notification visuelle riche selon le type d'erreur
            if (modelCheck.isNotAvailable()) {
                fr.baretto.ollamassist.core.agent.ui.ModelNotAvailableNotification.showModelNotAvailable(project, modelCheck);
            } else if (modelCheck.isError()) {
                fr.baretto.ollamassist.core.agent.ui.ModelNotAvailableNotification.showModelCheckError(project, modelCheck);
            } else if (modelCheck.isNotConfigured()) {
                fr.baretto.ollamassist.core.agent.ui.ModelNotAvailableNotification.showModelNotConfigured(project);
            }

            // Notifier également via MessageBus pour la compatibilité
            project.getMessageBus()
                    .syncPublisher(AgentTaskNotifier.TOPIC)
                    .agentProcessingFailed(userRequest, modelCheck.getUserMessage());
            return;
        }

        // Vérifier si le mode agent est disponible
        if (!agentSettings.isAgentModeAvailable()) {
            log.warn("Agent mode is not available, falling back to chat only");
            executeChatRequestWithStreaming(userRequest);
            return;
        }

        try {
            // Détecter si c'est une action de développement ou une question
            if (isActionRequest(userRequest)) {
                log.info("🛠️ ACTION detected - using development agent (streaming)");
                executeActionRequestWithStreaming(userRequest);
            } else {
                log.info("💬 QUESTION detected - using RAG chat (streaming)");
                executeChatRequestWithStreaming(userRequest);
            }

        } catch (Exception e) {
            log.error("🚀 ERROR in unified streaming agent execution", e);
            // Notifier l'erreur via MessageBus
            project.getMessageBus()
                    .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                    .agentProcessingFailed(userRequest, e.getMessage());
        }
    }

    /**
     * Méthode legacy pour compatibilité
     */
    public CompletableFuture<String> executeUserRequest(String userRequest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("🚀 UNIFIED AGENT: Processing user request: {}", userRequest);

                // Détecter si c'est une action de développement ou une question
                if (isActionRequest(userRequest)) {
                    log.info("🛠️ ACTION detected - using development agent");
                    return executeActionRequest(userRequest);
                } else {
                    log.info("💬 QUESTION detected - using RAG chat");
                    return executeChatRequest(userRequest);
                }

            } catch (Exception e) {
                log.error("🚀 ERROR in unified agent execution", e);
                return "❌ Erreur lors de l'exécution: " + e.getMessage();
            }
        });
    }

    /**
     * Détecte si la requête est une action de développement
     */
    private boolean isActionRequest(String userRequest) {
        String lower = userRequest.toLowerCase();

        log.error("🔍 DEBUG: Analyzing request: '{}'", userRequest);

        // Exclure les phrases de clarification ou de reformulation UNIQUEMENT si explicitement mentionnées
        if (lower.startsWith("reformule") || lower.startsWith("clarifi") || lower.startsWith("explique") ||
                lower.contains("qu'est-ce que") || lower.contains("comment ça") ||
                lower.contains("je veux dire") || lower.contains("en fait")) {
            log.error("🔍 DEBUG: isActionRequest('{}') = false (reformulation explicite détectée)", userRequest);
            return false;
        }

        // Simplifier la détection : si contient un verbe de création + classe/fichier
        boolean isAction = (lower.contains("crée") || lower.contains("créer") || lower.contains("créé") || lower.contains("create")) &&
                (lower.contains("classe") || lower.contains("class") || lower.contains("fichier") || lower.contains("file"));

        // Ajouter d'autres actions simples
        isAction = isAction || lower.contains("commit") || lower.contains("push") || lower.contains("build") ||
                lower.contains("compile") || lower.contains("test") || lower.contains("refactor");

        log.error("🔍 DEBUG: isActionRequest('{}') = {}", userRequest, isAction);
        return isAction;
    }

    /**
     * Exécute une requête d'action via l'agent de développement
     */
    private String executeActionRequest(String userRequest) {
        try {
            if (useNativeTools) {
                // ✨ NEW: Use explicit ReAct loop controller
                log.info("🔧 NATIVE MODE: Using explicit ReAct loop controller");
                ReActLoopController controller = new ReActLoopController(
                        project,
                        developmentAgent,
                        ollamaService
                );

                try {
                    ReActResult result = controller.executeWithLoop(userRequest).get();

                    if (result.isSuccess()) {
                        return result.getFinalMessage();
                    } else {
                        return result.getUserMessage();
                    }
                } finally {
                    controller.cleanup();
                }
            } else {
                // MODE FALLBACK: Parser le JSON et exécuter manuellement
                log.info("📄 FALLBACK MODE: Using JSON parsing");

                String systemPrompt = buildSystemPrompt();
                String fullRequest = systemPrompt + "\n\nUser request: " + userRequest;
                String jsonResult = agentInterface.executeRequest(fullRequest);
                return parseAndExecuteActions(jsonResult);
            }
        } catch (Exception e) {
            log.error("Error executing action request", e);
            return "❌ Erreur lors de l'exécution de l'action: " + e.getMessage();
        }
    }

    /**
     * Exécute une requête de chat avec streaming RAG
     */
    private void executeChatRequestWithStreaming(String userRequest) {
        try {
            if (ollamaService != null && ollamaService.getAssistant() != null) {
                log.info("💬 Using OllamaService with RAG for streaming chat");

                // Notifier le démarrage
                log.error("🔍 DEBUG: Publishing agentProcessingStarted for: {}", userRequest);
                project.getMessageBus()
                        .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                        .agentProcessingStarted(userRequest);

                // Démarrer le streaming via OllamaService comme dans l'ancien système
                // mais en utilisant les notifications d'agent
                StringBuilder fullResponse = new StringBuilder();

                ollamaService.getAssistant().chat(userRequest)
                        .onPartialResponse(token -> {
                            fullResponse.append(token);
                            // Publier chaque token en temps réel
                            project.getMessageBus()
                                    .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                                    .agentStreamingToken(token);
                        })
                        .onCompleteResponse(chatResponse -> {
                            project.getMessageBus()
                                    .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                                    .agentProcessingCompleted(userRequest, fullResponse.toString());
                        })
                        .onError(throwable -> {
                            project.getMessageBus()
                                    .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                                    .agentProcessingFailed(userRequest, throwable.getMessage());
                        })
                        .start();

            } else {
                log.warn("OllamaService not available, falling back to simple agent");
                executeActionRequestAndNotify(userRequest);
            }
        } catch (Exception e) {
            log.error("Error executing streaming chat request", e);
            project.getMessageBus()
                    .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                    .agentProcessingFailed(userRequest, e.getMessage());
        }
    }

    /**
     * Exécute une requête d'action avec streaming via l'agent de développement
     */
    private void executeActionRequestWithStreaming(String userRequest) {
        try {
            if (ollamaService != null && ollamaService.getAssistant() != null) {
                log.info("🛠️ Using OllamaService with agent tools for streaming action");

                // Notifier le démarrage
                project.getMessageBus()
                        .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                        .agentProcessingStarted(userRequest);

                // Utiliser le streaming pour les actions agent aussi
                StringBuilder fullResponse = new StringBuilder();

                if (useNativeTools) {
                    // MODE NATIF: Utiliser ReAct pattern avec streaming
                    String reactPrompt = buildReActPrompt(userRequest);

                    ollamaService.getAssistant().chat(reactPrompt)
                            .onPartialResponse(token -> {
                                fullResponse.append(token);
                                // Publier chaque token en temps réel
                                project.getMessageBus()
                                        .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                                        .agentStreamingToken(token);
                            })
                            .onCompleteResponse(chatResponse -> {
                                String finalResponse = fullResponse.toString();
                                log.info("🛠️ Agent streaming completed: {}", finalResponse);

                                // Notifier la fin du processing
                                project.getMessageBus()
                                        .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                                        .agentProcessingCompleted(userRequest, finalResponse);
                            })
                            .onError(throwable -> {
                                project.getMessageBus()
                                        .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                                        .agentProcessingFailed(userRequest, throwable.getMessage());
                            })
                            .start();
                } else {
                    // MODE FALLBACK: JSON parsing avec streaming
                    String systemPrompt = buildSystemPrompt();
                    String fullRequest = systemPrompt + "\n\nUser request: " + userRequest;

                    ollamaService.getAssistant().chat(fullRequest)
                            .onPartialResponse(token -> {
                                fullResponse.append(token);
                                // Publier chaque token en temps réel
                                project.getMessageBus()
                                        .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                                        .agentStreamingToken(token);
                            })
                            .onCompleteResponse(chatResponse -> {
                                String jsonResult = fullResponse.toString();
                                log.info("🛠️ Agent JSON response streaming completed");

                                // Parser et exécuter les actions du JSON
                                String executionResult = parseAndExecuteActions(jsonResult);

                                // Notifier la fin avec le résultat d'exécution
                                project.getMessageBus()
                                        .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                                        .agentProcessingCompleted(userRequest, executionResult);
                            })
                            .onError(throwable -> {
                                project.getMessageBus()
                                        .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                                        .agentProcessingFailed(userRequest, throwable.getMessage());
                            })
                            .start();
                }

            } else {
                log.warn("OllamaService not available, falling back to simple agent");
                executeActionRequestAndNotify(userRequest);
            }
        } catch (Exception e) {
            log.error("Error executing streaming action request", e);
            project.getMessageBus()
                    .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                    .agentProcessingFailed(userRequest, e.getMessage());
        }
    }

    /**
     * Propose une action et attend la validation utilisateur
     */
    private void executeActionRequestAndNotify(String userRequest) {
        // Notifier le démarrage
        project.getMessageBus()
                .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                .agentProcessingStarted(userRequest);

        try {
            // Analyser la demande pour créer des tâches appropriées
            List<fr.baretto.ollamassist.core.agent.task.Task> proposedTasks = analyzeRequestAndCreateTasks(userRequest);

            if (proposedTasks.isEmpty()) {
                // Pas d'action détectée, passer en mode chat
                log.info("No actionable tasks detected, falling back to chat");
                project.getMessageBus()
                        .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                        .agentProcessingCompleted(userRequest, "Aucune action spécifique détectée. Puis-je vous aider avec autre chose ?");
                return;
            }

            // Vérifier si les tâches peuvent être auto-approuvées selon les settings
            if (shouldAutoApproveAllTasks(proposedTasks)) {
                log.info("Auto-approving {} tasks based on security settings", proposedTasks.size());
                executeApprovedTasks(proposedTasks, userRequest);
                return;
            }

            // Afficher la proposition d'actions à l'utilisateur via MessagesPanel
            fr.baretto.ollamassist.core.agent.ui.ActionProposalCard.ActionValidator validator = new fr.baretto.ollamassist.core.agent.ui.ActionProposalCard.ActionValidator() {
                @Override
                public void approveActions(List<fr.baretto.ollamassist.core.agent.task.Task> tasks) {
                    executeApprovedTasks(tasks, userRequest);
                }

                @Override
                public void rejectActions(List<fr.baretto.ollamassist.core.agent.task.Task> tasks) {
                    project.getMessageBus()
                            .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                            .agentProcessingCompleted(userRequest, "❌ Actions rejetées par l'utilisateur.");
                }

                @Override
                public void modifyActions(List<fr.baretto.ollamassist.core.agent.task.Task> tasks) {
                    project.getMessageBus()
                            .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                            .agentProcessingCompleted(userRequest, "🔄 Modification des actions demandée. Veuillez reformuler votre demande.");
                }
            };

            // Publier la demande de validation via l'event bus avec ActionProposalCard
            project.getMessageBus()
                    .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                    .agentProposalRequested(userRequest, proposedTasks, validator);

        } catch (Exception e) {
            log.error("Error analyzing request for actions", e);
            project.getMessageBus()
                    .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                    .agentProcessingFailed(userRequest, "Erreur lors de l'analyse de la demande: " + e.getMessage());
        }
    }

    /**
     * Analyse la demande utilisateur et crée les tâches correspondantes
     */
    private List<fr.baretto.ollamassist.core.agent.task.Task> analyzeRequestAndCreateTasks(String userRequest) {
        List<fr.baretto.ollamassist.core.agent.task.Task> tasks = new ArrayList<>();
        String lower = userRequest.toLowerCase();

        log.error("🔍 DEBUG: analyzeRequestAndCreateTasks for: '{}'", userRequest);

        // Détection de création de classe Java ou fichier
        boolean hasCreateVerb = lower.contains("crée") || lower.contains("créer") || lower.contains("créé") || lower.contains("create");
        boolean hasClass = lower.contains("classe") || lower.contains("class");
        boolean hasFile = lower.contains("fichier") || lower.contains("file");

        log.error("🔍 DEBUG: hasCreateVerb={}, hasClass={}, hasFile={}", hasCreateVerb, hasClass, hasFile);

        if (hasCreateVerb) {
            if (hasClass) {
                log.error("🔍 DEBUG: Creating Java class task");
                tasks.add(createJavaClassTask(userRequest));
            } else if (hasFile) {
                log.error("🔍 DEBUG: Creating file task");
                tasks.add(createFileTask(userRequest));
            }
        }

        log.error("🔍 DEBUG: Created {} tasks", tasks.size());
        return tasks;
    }

    /**
     * Crée une tâche de création de classe Java
     */
    private fr.baretto.ollamassist.core.agent.task.Task createJavaClassTask(String userRequest) {
        return fr.baretto.ollamassist.core.agent.task.Task.builder()
                .id(java.util.UUID.randomUUID().toString())
                .description("Créer une classe Java basée sur: " + userRequest)
                .type(fr.baretto.ollamassist.core.agent.task.Task.TaskType.FILE_OPERATION)
                .priority(fr.baretto.ollamassist.core.agent.task.Task.TaskPriority.NORMAL)
                .parameters(Map.of("userRequest", userRequest, "taskType", "createJavaClass"))
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }

    /**
     * Crée une tâche de création de fichier générique
     */
    private fr.baretto.ollamassist.core.agent.task.Task createFileTask(String userRequest) {
        return fr.baretto.ollamassist.core.agent.task.Task.builder()
                .id(java.util.UUID.randomUUID().toString())
                .description("Créer un fichier basé sur: " + userRequest)
                .type(fr.baretto.ollamassist.core.agent.task.Task.TaskType.FILE_OPERATION)
                .priority(fr.baretto.ollamassist.core.agent.task.Task.TaskPriority.NORMAL)
                .parameters(Map.of("userRequest", userRequest, "taskType", "createFile"))
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }

    /**
     * Construit le message de proposition d'actions
     */
    private String buildProposalMessage(List<fr.baretto.ollamassist.core.agent.task.Task> tasks) {
        StringBuilder message = new StringBuilder();
        message.append("🤖 **Proposition d'actions:**\n\n");

        for (int i = 0; i < tasks.size(); i++) {
            fr.baretto.ollamassist.core.agent.task.Task task = tasks.get(i);
            message.append(String.format("%d. **%s**\n", i + 1, task.getDescription()));
            message.append(String.format("   - Type: %s\n", task.getType().name()));
            message.append(String.format("   - Priorité: %s\n\n", task.getPriority().name()));
        }

        message.append("⚠️ **Ces actions nécessitent votre validation avant exécution.**\n");
        message.append("Utilisez les boutons d'action pour approuver, rejeter ou modifier ces propositions.");

        return message.toString();
    }

    /**
     * Exécute les tâches approuvées par l'utilisateur
     */
    private void executeApprovedTasks(List<fr.baretto.ollamassist.core.agent.task.Task> tasks, String originalRequest) {
        CompletableFuture.supplyAsync(() -> {
                    StringBuilder results = new StringBuilder();
                    results.append("✅ **Exécution des actions approuvées:**\n\n");

                    for (fr.baretto.ollamassist.core.agent.task.Task task : tasks) {
                        task.markStarted();
                        try {
                            String taskResult = executeTask(task);
                            task.markCompleted();
                            results.append(String.format("✓ %s\n%s\n\n", task.getDescription(), taskResult));
                        } catch (Exception e) {
                            task.markFailed(e.getMessage());
                            results.append(String.format("❌ %s\nErreur: %s\n\n", task.getDescription(), e.getMessage()));
                        }
                    }

                    return results.toString();
                }).thenAccept(result ->
                        project.getMessageBus()
                                .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                                .agentProcessingCompleted(originalRequest, result))
                .exceptionally(throwable -> {
                    project.getMessageBus()
                            .syncPublisher(fr.baretto.ollamassist.core.agent.AgentTaskNotifier.TOPIC)
                            .agentProcessingFailed(originalRequest, "Erreur lors de l'exécution: " + throwable.getMessage());
                    return null;
                });
    }

    /**
     * Exécute une tâche individuelle
     */
    private String executeTask(fr.baretto.ollamassist.core.agent.task.Task task) {
        String taskType = task.getParameter("taskType", String.class);
        String userRequest = task.getParameter("userRequest", String.class);

        if ("createJavaClass".equals(taskType)) {
            return executeActionRequest(userRequest);
        } else if ("createFile".equals(taskType)) {
            return executeActionRequest(userRequest);
        }

        return "Type de tâche non supporté: " + taskType;
    }

    /**
     * Exécute une requête de chat via le système RAG (version legacy synchrone)
     */
    private String executeChatRequest(String userRequest) {
        try {
            if (ollamaService != null && ollamaService.getAssistant() != null) {
                log.info("💬 Using OllamaService with RAG for chat");

                // Collecter la réponse en streaming de manière synchrone
                StringBuilder response = new StringBuilder();
                CompletableFuture<String> chatFuture = new CompletableFuture<>();

                try {
                    ollamaService.getAssistant().chat(userRequest)
                            .onPartialResponse(response::append)
                            .onCompleteResponse(chatResponse ->
                                    chatFuture.complete(response.toString()))
                            .onError(chatFuture::completeExceptionally)
                            .start();

                    // Attendre la réponse avec timeout
                    return chatFuture.get(60, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("Failed to use streaming, falling back to simple interface");
                    return agentInterface.executeRequest(userRequest);
                }
            } else {
                log.warn("OllamaService not available, falling back to simple chat");
                return agentInterface.executeRequest(userRequest);
            }
        } catch (Exception e) {
            log.error("Error executing chat request", e);
            return "❌ Erreur lors de la conversation: " + e.getMessage();
        }
    }

    /**
     * Construit le prompt ReAct pour l'agent (mode natif avec tools)
     */
    private String buildReActPrompt(String userRequest) {
        return String.format("""
                You are an expert IntelliJ IDEA development assistant that follows the ReAct (Reasoning and Acting) pattern.
                
                For every user request, you must think step by step and validate your work:
                
                1. ANALYZE the request and plan your approach
                2. EXECUTE actions using available tools
                3. VERIFY the results (especially for compilation)
                4. FIX any issues discovered
                5. CONTINUE until the task is complete
                
                CRITICAL: After creating any Java class or modifying code:
                - ALWAYS use compileAndCheckErrors() to verify compilation
                - If compilation fails with missing imports or other errors, FIX them immediately
                - Use getCompilationDiagnostics() to get detailed error information
                - Continue this process until compilation succeeds
                
                Available tools for your use:
                - createJavaClass: Create Java classes with proper structure
                - createFile: Create any file with custom content
                - executeGitCommand: Git operations (commit, push, pull, status)
                - buildProject: Build, test, or package the project
                - compileAndCheckErrors: Compile and check for errors
                - getCompilationDiagnostics: Get detailed compilation diagnostics
                - analyzeCode: Analyze existing project code
                - searchWeb: Search for information when needed
                
                WORK ITERATIVELY: Think -> Act -> Observe -> Think -> Act until complete.
                
                USER REQUEST: %s
                
                Begin by thinking about your approach, then execute the necessary steps.
                """, userRequest);
    }

    /**
     * Construit le prompt système pour l'agent (mode fallback JSON)
     */
    private String buildSystemPrompt() {
        return """
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
                
                Example for creating a HelloWorld class:
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
                  "message": "Classe HelloWorld créée avec succès avec une méthode sayHello()"
                }
                ```
                
                IMPORTANT:
                - Always use relative file paths from project root
                - Always include proper Java package declarations when creating Java files
                - Always respond in French for the message, but write code in English
                - Always return valid JSON - no additional text before or after
                """;
    }

    /**
     * Vérifie si l'agent est disponible
     */
    public boolean isAvailable() {
        return developmentAgent != null && developmentAgent.isAvailable() && chatModel != null;
    }

    /**
     * Indique si l'agent utilise les tools natifs ou le fallback JSON
     */
    public boolean isUsingNativeTools() {
        return useNativeTools;
    }

    /**
     * Obtient les statistiques de l'agent
     */
    public AgentStats getStats() {
        return developmentAgent.getStats();
    }

    /**
     * Obtient l'agent de développement sous-jacent
     */
    public IntelliJDevelopmentAgent getDevelopmentAgent() {
        return developmentAgent;
    }

    /**
     * Parse le JSON retourné par Ollama et exécute les actions correspondantes
     */
    private String parseAndExecuteActions(String jsonResponse) {
        try {
            log.error("🔍 PARSING JSON: {}", jsonResponse);

            // Extraire le JSON si c'est dans des ```json
            String cleanJson = extractJsonFromResponse(jsonResponse);
            log.error("🔍 CLEAN JSON: {}", cleanJson);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(cleanJson);

            StringBuilder resultBuilder = new StringBuilder();
            JsonNode actionsNode = root.get("actions");
            JsonNode messageNode = root.get("message");

            if (actionsNode != null && actionsNode.isArray()) {
                for (JsonNode actionNode : actionsNode) {
                    String tool = actionNode.get("tool").asText();
                    JsonNode parametersNode = actionNode.get("parameters");

                    log.error("🔧 EXECUTING TOOL: {} with params: {}", tool, parametersNode);

                    String toolResult = executeToolAction(tool, parametersNode);
                    resultBuilder.append(toolResult).append("\n");
                }
            }

            // Ajouter le message de l'agent
            if (messageNode != null) {
                resultBuilder.append("\n📝 ").append(messageNode.asText());
            }

            return resultBuilder.toString();

        } catch (Exception e) {
            log.error("❌ Erreur lors du parsing JSON: {}", e.getMessage(), e);
            return "❌ Erreur lors de l'exécution: " + e.getMessage() + "\n\nRéponse brute: " + jsonResponse;
        }
    }

    /**
     * Extrait le JSON de la réponse (retire les ```json si présents)
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
     * Exécute une action tool spécifique
     */
    private String executeToolAction(String tool, JsonNode parameters) {
        log.error("🛠️ EXECUTING: {} with {}", tool, parameters);

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
                    return "❌ Tool inconnu: " + tool;
            }
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'exécution du tool {}: {}", tool, e.getMessage(), e);
            return "❌ Erreur lors de l'exécution du tool " + tool + ": " + e.getMessage();
        }
    }

    /**
     * Vérifie si toutes les tâches proposées peuvent être auto-approuvées
     */
    private boolean shouldAutoApproveAllTasks(List<fr.baretto.ollamassist.core.agent.task.Task> tasks) {
        if (!agentSettings.isAgentModeEnabled()) {
            return false;
        }

        for (fr.baretto.ollamassist.core.agent.task.Task task : tasks) {
            AgentModeSettings.AgentActionType actionType = mapTaskToActionType(task);
            if (!agentSettings.shouldAutoApprove(actionType)) {
                log.info("Task {} requires approval (action type: {})", task.getDescription(), actionType);
                return false;
            }
        }

        log.info("All {} tasks can be auto-approved according to security level: {}",
                tasks.size(), agentSettings.getSecurityLevel());
        return true;
    }

    /**
     * Mappe un Task vers un AgentActionType pour évaluation de sécurité
     */
    private AgentModeSettings.AgentActionType mapTaskToActionType(fr.baretto.ollamassist.core.agent.task.Task task) {
        if (task.getType() == null) {
            return AgentModeSettings.AgentActionType.EXECUTE_COMMAND; // Conservateur par défaut
        }

        return switch (task.getType()) {
            case FILE_OPERATION -> {
                String operation = task.getParameter("operation", String.class);
                if ("create".equals(operation)) {
                    yield AgentModeSettings.AgentActionType.WRITE_FILE;
                } else if ("delete".equals(operation)) {
                    yield AgentModeSettings.AgentActionType.DELETE_FILE;
                } else {
                    yield AgentModeSettings.AgentActionType.MODIFY_SETTINGS;
                }
            }
            case CODE_ANALYSIS -> AgentModeSettings.AgentActionType.READ_FILE;
            case GIT_OPERATION -> {
                String operation = task.getParameter("operation", String.class);
                if ("commit".equals(operation)) {
                    yield AgentModeSettings.AgentActionType.GIT_COMMIT;
                } else if ("push".equals(operation)) {
                    yield AgentModeSettings.AgentActionType.GIT_PUSH;
                } else {
                    yield AgentModeSettings.AgentActionType.GIT_COMMIT;
                }
            }
            case BUILD_OPERATION -> AgentModeSettings.AgentActionType.BUILD_OPERATION;
            case MCP_OPERATION -> {
                // Analyser le type d'opération MCP
                String operation = task.getParameter("operation", String.class);
                if ("search".equals(operation)) {
                    yield AgentModeSettings.AgentActionType.WEB_SEARCH;
                } else {
                    yield AgentModeSettings.AgentActionType.READ_FILE;
                }
            }
            default -> AgentModeSettings.AgentActionType.EXECUTE_COMMAND; // Conservateur par défaut
        };
    }

    /**
     * Vérifie si le mode agent est disponible et configuré correctement
     */
    public boolean isAgentModeAvailable() {
        return agentSettings.isAgentModeAvailable();
    }

    /**
     * Obtient un résumé de la configuration agent
     */
    public String getAgentConfigurationSummary() {
        return agentSettings.getConfigurationSummary();
    }

    /**
     * Interface pour l'agent LangChain4J
     */
    public interface AgentInterface {
        /**
         * Exécute une demande de développement utilisateur
         *
         * @param userRequest la demande de l'utilisateur
         * @return la réponse de l'agent avec les résultats des tools exécutés
         */
        String executeRequest(String userRequest);
    }
}