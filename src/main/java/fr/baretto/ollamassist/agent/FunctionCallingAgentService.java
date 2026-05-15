package fr.baretto.ollamassist.agent;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import fr.baretto.ollamassist.agent.tools.ToolRateLimiter;
import fr.baretto.ollamassist.agent.tools.files.SourceRootResolver;
import fr.baretto.ollamassist.auth.AuthenticationHelper;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import fr.baretto.ollamassist.setting.OllamaSettings;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the agent ReAct loop via a manual streaming loop over LangChain4j's
 * low-level {@link StreamingChatModel} API.
 *
 * <p>Architecture (AGENT_ARCH.md):
 * <pre>
 *   User Goal → [Thought] LLM reasons → [Action] tool call → [Observation] tool result
 *             → [Thought] LLM adapts → ... → [Final Answer]
 * </pre>
 *
 * <p>Two execution paths per turn (AGENT_ARCH.md Rule 1):
 * <ol>
 *   <li><b>Native FC</b> — model emits proper {@code tool_calls}; handled via
 *       {@code onCompleteToolCall} callback.</li>
 *   <li><b>Text fallback</b> — model emits JSON text tool calls (e.g. qwen3/Ollama
 *       streaming regression after error observations); detected by
 *       {@link TextToolCallParser} in {@code onCompleteResponse}.</li>
 * </ol>
 *
 * <p>Guards per execution (AGENT_ARCH.md Rule 3):
 * <ul>
 *   <li>{@value #MAX_TOOL_CALLS_PER_EXECUTION} total tool calls — abort via {@link AgentToolProvider#abort()}</li>
 *   <li>Per-tool limits — enforced by {@link ToolRateLimiter}</li>
 *   <li>Timeout — configured on {@link OllamaStreamingChatModel}</li>
 *   <li>Loop detection — same (tool, arg) repeated {@value LoopDetector#STUCK_THRESHOLD}+ times</li>
 *   <li>Progress check at {@value #PROGRESS_CHECK_AT} calls</li>
 * </ul>
 */
@Service(Service.Level.PROJECT)
@Slf4j
public final class FunctionCallingAgentService implements Disposable {

    public static final int MAX_TOOL_CALLS_PER_EXECUTION = 30;
    static final int PROGRESS_CHECK_AT = 20;

    private static final String TOOL_READ_FILE          = "readFile";
    private static final String TOOL_EDIT_FILE          = "editFile";
    private static final String TOOL_WRITE_FILE         = "writeFile";
    private static final String TOOL_SEARCH_WEB         = "searchWeb";
    private static final String TOOL_SEARCH_WORKSPACE   = "searchWorkspace";
    private static final String TOOL_SEARCH_KNOWLEDGE   = "searchKnowledgeBase";
    private static final String PARAM_QUERY             = "query";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BASIC_AUTH_FORMAT = "Basic %s";

    private static final String SYSTEM_PROMPT = """
            You are an autonomous software development assistant operating inside a JetBrains IDE.
            You have access to tools that let you read and modify the project, search the web, \
            and query the project knowledge base.

            ## CRITICAL: When to use tools vs. when to answer directly

            Tools are ONLY for tasks that require acting on the project: reading files, writing \
            or editing code, searching the codebase or the web.

            If the user asks a conversational or informational question — such as "what can you do?", \
            "how does X work?", "explain Y", "can you help me with Z?", or any question that does \
            not require touching files — answer directly in plain text. Do NOT invoke any tool. \
            Calling a file-writing tool in response to a conversational question is WRONG.

            ## CRITICAL: How to invoke tools

            You MUST use the function-calling mechanism to invoke tools. NEVER output tool calls \
            as JSON text, XML, markdown code blocks, or any other text format. If you write \
            {"name": "..."} or <tool_call> in your response, the tool will NOT execute. \
            Always invoke tools directly through the provided function interface.

            ## How to work

            Think step by step before calling any tool. After each tool result, reflect on what \
            you learned, then decide your next action. This is the ReAct pattern: Reason → Act → Observe → Repeat.

            Do not attempt to do everything in one tool call. Break the task into small, verifiable steps.
            Continue calling tools until the task is fully complete — do not stop after only one step.

            ## Rules

            - Never write files outside the project root.
            - Never hard-code secrets (passwords, API keys, tokens) in files.
            - Always call readFile before editFile. You cannot construct a correct search/replace \
              without knowing the exact file content.
            - If a tool returns an ERROR, analyse the error message and retry with corrected \
              parameters. NEVER display what the result "should look like" if the tool has not \
              confirmed success — that would mislead the user into thinking the task is done.
            - After every editFile call, call readFile to verify the change was applied correctly.
            - For well-known algorithms and standard patterns (FizzBuzz, sorting, fibonacci, etc.), \
              write the code directly from your training knowledge. Do NOT search the web or \
              knowledge base for trivially known code — just write it.
            - NEVER say "I will write the code" or "I will create the file" in a final answer \
              without having actually called writeFile or editFile first. If you intend to create \
              or modify a file, call the tool immediately — do not promise it in text.
            - ALWAYS use the full relative path when calling writeFile or editFile. The goal \
              message tells you the project source roots — use them as prefixes. \
              If the project structure is unknown, call searchWorkspace first to find out \
              where existing source files live, then use the same directory structure. \
              NEVER pass a bare filename (e.g. "MyClass.java") — it will land at the project root.
            - If you cannot complete the goal with the available tools, explain clearly what is \
              missing and what the user should do manually.
            - When the maximum number of tool calls is reached, summarise what you have accomplished \
              and what remains to be done.

            ## Final answer

            When you have completed the goal (or determined it cannot be completed), provide a \
            clear, concise summary of what was done, what files were changed, and any next steps \
            the user should take.
            """;

    private final Project project;
    private final ToolRateLimiter rateLimiter;
    final AgentToolProvider toolProvider;          // package-private for tests
    private volatile boolean disposed = false;
    @org.jetbrains.annotations.Nullable
    private final StreamingChatModel overrideModel; // injected in platform tests

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public FunctionCallingAgentService(@NotNull Project project) {
        this.project = project;
        this.rateLimiter = new ToolRateLimiter();
        this.toolProvider = new AgentToolProvider(project, rateLimiter);
        this.overrideModel = null;
    }

    @TestOnly
    FunctionCallingAgentService(Project project,
                                AgentToolProvider toolProvider,
                                ToolRateLimiter rateLimiter) {
        this.project = project;
        this.toolProvider = toolProvider;
        this.rateLimiter = rateLimiter;
        this.overrideModel = null;
    }

    @TestOnly
    public FunctionCallingAgentService(Project project,
                                       AgentToolProvider toolProvider,
                                       StreamingChatModel model) {
        this.project = project;
        this.rateLimiter = new ToolRateLimiter();
        this.toolProvider = toolProvider;
        this.overrideModel = model;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Starts a ReAct execution for the given {@code goal}.
     * Callbacks are delivered on a background thread.
     */
    public void execute(@NotNull String goal, @NotNull AgentStreamHandler handler) {
        if (disposed) {
            handler.onError(new IllegalStateException("FunctionCallingAgentService has been disposed"));
            return;
        }

        rateLimiter.reset();
        toolProvider.resetAbort();

        List<ToolSpecification> toolSpecs = withPluginClassLoader(
                () -> ToolSpecifications.toolSpecificationsFrom(toolProvider));

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(dev.langchain4j.data.message.SystemMessage.from(SYSTEM_PROMPT));
        messages.add(dev.langchain4j.data.message.UserMessage.from(buildGoalWithContext(goal)));

        StreamingChatModel model = overrideModel != null ? overrideModel : buildProductionModel();
        runReActTurn(model, messages, toolSpecs, handler, ExecutionContext.create());
    }

    // -------------------------------------------------------------------------
    // ReAct loop
    // -------------------------------------------------------------------------

    private void runReActTurn(StreamingChatModel model,
                               List<ChatMessage> messages,
                               List<ToolSpecification> toolSpecs,
                               AgentStreamHandler handler,
                               ExecutionContext ctx) {

        StringBuilder textBuffer = new StringBuilder();
        List<ToolExecPair> nativeResults = new ArrayList<>();

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(toolSpecs)
                .build();

        StreamingChatResponseHandler responseHandler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String token) {
                textBuffer.append(token);
                handler.onToken(token);
            }

            @Override
            public void onCompleteToolCall(CompleteToolCall ctc) {
                ToolExecutionRequest req = ctc.toolExecutionRequest();
                Map<String, String> args = TextToolCallParser.parseArgsJson(req.arguments());
                String primaryArg = extractPrimaryArg(req.name(), args);

                handler.onToolCall(req.name(), req.arguments());

                String result;
                if (ctx.loopDetector().isStuck(req.name(), primaryArg)) {
                    result = ctx.loopDetector().stuckMessage(req.name());
                    log.warn("Agent: loop detected on tool '{}' with arg '{}'", req.name(), primaryArg);
                } else {
                    ctx.loopDetector().track(req.name(), primaryArg);
                    result = enrichError(req.name(), dispatchNative(req));
                }

                handler.onToolResult(req.name(), result);
                nativeResults.add(new ToolExecPair(req, result));

                if (ctx.toolCallCount().incrementAndGet() >= MAX_TOOL_CALLS_PER_EXECUTION) {
                    log.warn("Agent: MAX_TOOL_CALLS_PER_EXECUTION ({}) reached — aborting",
                            MAX_TOOL_CALLS_PER_EXECUTION);
                    toolProvider.abort();
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                messages.add(response.aiMessage());

                // --- Native FC path ---
                if (!nativeResults.isEmpty()) {
                    for (ToolExecPair pair : nativeResults) {
                        messages.add(ToolExecutionResultMessage.from(pair.request(), pair.result()));
                    }
                    continueOrComplete(model, messages, toolSpecs, handler, ctx);
                    return;
                }

                // --- Text-based fallback ---
                List<TextToolCallParser.ParsedToolCall> textCalls =
                        TextToolCallParser.parse(textBuffer.toString());

                if (!textCalls.isEmpty() && !toolProvider.isAborted()) {
                    log.debug("Agent: text-based tool call fallback ({} call(s))", textCalls.size());
                    executeTextFallback(textCalls, textBuffer.toString(),
                            model, messages, toolSpecs, handler, ctx);
                    return;
                }

                // --- Final answer ---
                handler.onComplete();
            }

            @Override
            public void onError(Throwable error) {
                log.error("Agent execution failed: {}", error.getMessage(), error);
                handler.onError(error);
            }
        };

        withPluginClassLoader(() -> {
            model.chat(request, responseHandler);
            return null;
        });
    }

    /**
     * Decides whether to continue the loop or stop, and injects a progress check
     * message when the tool call count crosses {@value #PROGRESS_CHECK_AT}.
     */
    private void continueOrComplete(StreamingChatModel model,
                                     List<ChatMessage> messages,
                                     List<ToolSpecification> toolSpecs,
                                     AgentStreamHandler handler,
                                     ExecutionContext ctx) {
        if (toolProvider.isAborted()) {
            handler.onComplete();
            return;
        }
        injectProgressCheckIfNeeded(messages, ctx);
        runReActTurn(model, messages, toolSpecs, handler, ctx);
    }

    /**
     * Injects a progress-check user message once when the tool call count crosses
     * {@value #PROGRESS_CHECK_AT}, prompting the model to assess its remaining budget.
     */
    private void injectProgressCheckIfNeeded(List<ChatMessage> messages, ExecutionContext ctx) {
        int count = ctx.toolCallCount().get();
        if (count >= PROGRESS_CHECK_AT && ctx.progressCheckSent().compareAndSet(false, true)) {
            int remaining = MAX_TOOL_CALLS_PER_EXECUTION - count;
            String msg = ("PROGRESS_CHECK: You have used %d of %d allowed tool calls (%d remaining). " +
                    "Please assess: (1) what have you accomplished so far? " +
                    "(2) what key steps remain? " +
                    "(3) can you complete the goal within %d more calls? " +
                    "If not, provide a partial answer now and explain what is missing.")
                    .formatted(count, MAX_TOOL_CALLS_PER_EXECUTION, remaining, remaining);
            log.debug("Agent: injecting progress check at {} tool calls", count);
            messages.add(dev.langchain4j.data.message.UserMessage.from(msg));
        }
    }

    private void executeTextFallback(List<TextToolCallParser.ParsedToolCall> calls,
                                      String fullResponseText,
                                      StreamingChatModel model,
                                      List<ChatMessage> messages,
                                      List<ToolSpecification> toolSpecs,
                                      AgentStreamHandler handler,
                                      ExecutionContext ctx) {

        messages.remove(messages.size() - 1);

        List<ToolExecutionRequest> syntheticReqs = calls.stream()
                .map(call -> ToolExecutionRequest.builder()
                        .id(UUID.randomUUID().toString())
                        .name(call.name())
                        .arguments(call.argsJson())
                        .build())
                .toList();

        String reasoning = TextToolCallParser.extractReasoningText(fullResponseText, calls);
        messages.add(new AiMessage(reasoning.isBlank() ? null : reasoning, syntheticReqs));

        for (int i = 0; i < calls.size(); i++) {
            TextToolCallParser.ParsedToolCall call = calls.get(i);
            ToolExecutionRequest req = syntheticReqs.get(i);
            String primaryArg = extractPrimaryArg(call.name(), call.args());

            handler.onToolCall(call.name(), call.argsJson());

            String result;
            if (ctx.loopDetector().isStuck(call.name(), primaryArg)) {
                result = ctx.loopDetector().stuckMessage(call.name());
                log.warn("Agent: loop detected (text fallback) on '{}' with arg '{}'",
                        call.name(), primaryArg);
            } else {
                ctx.loopDetector().track(call.name(), primaryArg);
                result = enrichError(call.name(), dispatchByName(call.name(), call.args()));
            }

            handler.onToolResult(call.name(), result);
            messages.add(ToolExecutionResultMessage.from(req, result));

            if (ctx.toolCallCount().incrementAndGet() >= MAX_TOOL_CALLS_PER_EXECUTION) {
                log.warn("Agent: MAX_TOOL_CALLS_PER_EXECUTION reached in text fallback");
                toolProvider.abort();
                break;
            }
        }

        continueOrComplete(model, messages, toolSpecs, handler, ctx);
    }

    // -------------------------------------------------------------------------
    // Tool dispatch
    // -------------------------------------------------------------------------

    private String dispatchNative(ToolExecutionRequest req) {
        Map<String, String> args = TextToolCallParser.parseArgsJson(req.arguments());
        return dispatchByName(req.name(), args);
    }

    private String dispatchByName(String name, Map<String, String> args) {
        return switch (name) {
            case TOOL_READ_FILE        -> toolProvider.readFile(args.get("path"));
            case TOOL_EDIT_FILE        -> dispatchEditFile(args);
            case TOOL_WRITE_FILE       -> toolProvider.writeFile(args.get("path"), args.get("content"));
            case TOOL_SEARCH_WEB       -> toolProvider.searchWeb(args.get(PARAM_QUERY));
            case TOOL_SEARCH_WORKSPACE -> toolProvider.searchWorkspace(args.get(PARAM_QUERY));
            case TOOL_SEARCH_KNOWLEDGE -> toolProvider.searchKnowledgeBase(args.get(PARAM_QUERY));
            default                    -> "ERROR: Unknown tool: " + name;
        };
    }

    private String dispatchEditFile(Map<String, String> args) {
        String search = args.get("search");
        String content = args.get("content");
        if ((search == null || search.isBlank()) && content != null && !content.isBlank()) {
            log.debug("Agent: redirecting editFile(content=...) → writeFile (model tool confusion)");
            return toolProvider.writeFile(args.get("path"), content);
        }
        return toolProvider.editFile(args.get("path"), search, args.get("replace"), args.get("replaceAll"));
    }

    // -------------------------------------------------------------------------
    // Error enrichment — adds a HINT to help the model self-correct
    // -------------------------------------------------------------------------

    private static String enrichError(String toolName, String result) {
        if (!result.startsWith("ERROR:")) return result;
        String lower = result.toLowerCase();
        String hint = switch (toolName) {
            case TOOL_READ_FILE -> {
                if (lower.contains("not found") || lower.contains("does not exist"))
                    yield "\nHINT: The file does not exist yet. Use writeFile to create it, " +
                          "or searchWorkspace to find the correct path.";
                yield "";
            }
            case TOOL_EDIT_FILE -> {
                if (lower.contains("not found") || lower.contains("does not exist"))
                    yield "\nHINT: The file does not exist. Use writeFile to create a new file, not editFile.";
                if (lower.contains("search") || lower.contains("not found in file"))
                    yield "\nHINT: The search string was not found. Call readFile first to read the exact " +
                          "current content, then copy the search fragment character-for-character.";
                yield "";
            }
            case TOOL_WRITE_FILE -> {
                if (lower.contains("already exist"))
                    yield "\nHINT: The file already exists. Use editFile to modify it.";
                yield "";
            }
            default -> "";
        };
        return result + hint;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String extractPrimaryArg(String toolName, Map<String, String> args) {
        return switch (toolName) {
            case TOOL_READ_FILE, TOOL_EDIT_FILE, TOOL_WRITE_FILE -> args.getOrDefault("path", "");
            case TOOL_SEARCH_WEB, TOOL_SEARCH_WORKSPACE, TOOL_SEARCH_KNOWLEDGE ->
                    args.getOrDefault(PARAM_QUERY, "");
            default -> args.values().stream().findFirst().orElse("");
        };
    }

    /**
     * Prepends the project's source roots to the goal so the model uses correct file paths.
     * Without this, models omit the source-root prefix (e.g. "FizzBuzzService.java" instead of
     * "src/main/java/FizzBuzzService.java") and files land at the project root.
     */
    private String buildGoalWithContext(String goal) {
        if (project == null) return goal;
        try {
            List<String> roots = SourceRootResolver.sourceRootRelativePaths(project);
            String context;
            if (!roots.isEmpty()) {
                String rootsLine = String.join(", ", roots);
                context = "Project source roots (use these as prefixes for all file paths): ["
                        + rootsLine + "]. "
                        + "NEVER use a bare filename — always include the source root in the path.";
            } else {
                // No source roots detected (project type unknown or not yet indexed).
                // Ask the model to inspect the project structure before creating files
                // rather than guessing or using hardcoded conventions.
                context = "Project source root structure unknown. "
                        + "Before creating any source file, call searchWorkspace or readFile "
                        + "on the project root to understand the directory layout, "
                        + "then use the correct full path relative to the project root.";
            }
            return context + "\n\n" + goal;
        } catch (Exception e) {
            log.debug("Could not resolve source roots for goal context: {}", e.getMessage());
            return goal;
        }
    }

    private StreamingChatModel buildProductionModel() {
        OllamaStreamingChatModel.OllamaStreamingChatModelBuilder builder =
                OllamaStreamingChatModel.builder()
                        .baseUrl(OllamAssistSettings.getInstance().getChatOllamaUrl())
                        .modelName(OllamaSettings.getInstance().getAgentModelName())
                        .temperature(0.3)
                        .timeout(Duration.ofMinutes(5));

        if (AuthenticationHelper.isAuthenticationConfigured()) {
            Map<String, String> headers = new HashMap<>();
            headers.put(AUTHORIZATION_HEADER,
                    String.format(BASIC_AUTH_FORMAT, AuthenticationHelper.createBasicAuthHeader()));
            builder.customHeaders(headers);
        }
        return builder.build();
    }

    private <T> T withPluginClassLoader(java.util.concurrent.Callable<T> callable) {
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(FunctionCallingAgentService.class.getClassLoader());
        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    private record ToolExecPair(ToolExecutionRequest request, String result) {}

    /**
     * Mutable execution state shared across all recursive turns of a single agent execution.
     * Created once in {@link #execute} and threaded through the entire ReAct loop.
     */
    record ExecutionContext(
            AtomicInteger toolCallCount,
            LoopDetector loopDetector,
            AtomicBoolean progressCheckSent
    ) {
        static ExecutionContext create() {
            return new ExecutionContext(new AtomicInteger(0), new LoopDetector(), new AtomicBoolean(false));
        }
    }

    /**
     * Detects repetitive tool call patterns (same tool + same primary argument appearing
     * too many times within a sliding window of {@value #WINDOW} calls).
     *
     * <p>Thresholds differ by tool category:
     * <ul>
     *   <li>Search tools — blocked on the 2nd identical call (same query adds no value)</li>
     *   <li>File tools   — blocked on the 3rd identical call (read-verify pattern is legitimate once)</li>
     * </ul>
     */
    static final class LoopDetector {

        static final int WINDOW = 8;
        static final int STUCK_THRESHOLD_SEARCH = 1; // block on 2nd identical search
        static final int STUCK_THRESHOLD_FILE   = 2; // block on 3rd identical file op

        private static final java.util.Set<String> SEARCH_TOOLS =
                java.util.Set.of(TOOL_SEARCH_KNOWLEDGE, TOOL_SEARCH_WEB, TOOL_SEARCH_WORKSPACE);

        private final Deque<String> recent = new ArrayDeque<>();

        void track(String toolName, String primaryArg) {
            recent.addLast(signature(toolName, primaryArg));
            if (recent.size() > WINDOW) recent.removeFirst();
        }

        boolean isStuck(String toolName, String primaryArg) {
            int threshold = SEARCH_TOOLS.contains(toolName)
                    ? STUCK_THRESHOLD_SEARCH
                    : STUCK_THRESHOLD_FILE;
            String sig = signature(toolName, primaryArg);
            return recent.stream().filter(sig::equals).count() >= threshold;
        }

        String stuckMessage(String toolName) {
            return ("ERROR: Loop detected — '" + toolName + "' was called with the same arguments " +
                    "multiple times without progress. " +
                    "You MUST break out of this loop: use a different approach, different parameters, " +
                    "or — if you have enough information — write the code/file NOW using writeFile or editFile " +
                    "instead of searching further.");
        }

        private static String signature(String toolName, String primaryArg) {
            return toolName + ":" + (primaryArg == null ? "" : primaryArg.trim().toLowerCase());
        }
    }
}
