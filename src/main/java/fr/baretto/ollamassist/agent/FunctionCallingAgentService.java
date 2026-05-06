package fr.baretto.ollamassist.agent;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import fr.baretto.ollamassist.agent.tools.ToolRateLimiter;
import fr.baretto.ollamassist.auth.AuthenticationHelper;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the agent ReAct loop via LangChain4j native function calling.
 *
 * <p>Architecture (see AGENT_ARCH.md):
 * <pre>
 *   User Goal → [Thought] LLM reasons → [Action] tool call → [Observation] tool result
 *             → [Thought] LLM adapts → ... → [Final Answer]
 * </pre>
 *
 * <p>Guards per execution (AGENT_ARCH.md Rule 3):
 * <ul>
 *   <li>{@value #MAX_TOOL_CALLS_PER_EXECUTION} total tool calls — abort via {@link AgentToolProvider#abort()}</li>
 *   <li>Per-tool limits — enforced by {@link ToolRateLimiter}</li>
 *   <li>Timeout — configured on {@link OllamaStreamingChatModel}</li>
 * </ul>
 */
@Service(Service.Level.PROJECT)
@Slf4j
public final class FunctionCallingAgentService implements Disposable {

    public static final int MAX_TOOL_CALLS_PER_EXECUTION = 30;
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BASIC_AUTH_FORMAT = "Basic %s";

    private final Project project;
    private final ToolRateLimiter rateLimiter;
    final AgentToolProvider toolProvider;          // package-private for tests
    private volatile boolean disposed = false;
    @org.jetbrains.annotations.Nullable
    private final StreamingChatModel overrideModel; // injected in platform tests

    // -------------------------------------------------------------------------
    // Inner agent interface (ReAct system prompt — AGENT_ARCH.md Rule 3 + Rule 7)
    // -------------------------------------------------------------------------

    interface ReactAgent {
        @SystemMessage("""
                You are an autonomous software development assistant operating inside a JetBrains IDE.
                You have access to tools that let you read and modify the project, search the web, \
                and query the project knowledge base.

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

                ## Available tools (use the exact function names below)

                - readFile: read the full content of an existing file
                - writeFile: create a new file (fails if file exists — use editFile instead)
                - editFile: modify an existing file by replacing a text fragment
                - searchWorkspace: keyword search across project files
                - searchKnowledgeBase: semantic search in the indexed project knowledge
                - searchWeb: DuckDuckGo search for external documentation

                ## Rules

                - Never write files outside the project root.
                - Never hard-code secrets (passwords, API keys, tokens) in files.
                - Always call readFile before editFile. You cannot construct a correct search/replace \
                  without knowing the exact file content.
                - If a tool returns an ERROR, analyse the error message and retry with corrected \
                  parameters. NEVER display what the result "should look like" if the tool has not \
                  confirmed success — that would mislead the user into thinking the task is done.
                - After every editFile call, call readFile to verify the change was applied correctly.
                - If you cannot complete the goal with the available tools, explain clearly what is \
                  missing and what the user should do manually.
                - When the maximum number of tool calls is reached, summarise what you have accomplished \
                  and what remains to be done.

                ## Final answer

                When you have completed the goal (or determined it cannot be completed), provide a \
                clear, concise summary of what was done, what files were changed, and any next steps \
                the user should take.
                """)
        TokenStream execute(@UserMessage String goal);
    }

    // -------------------------------------------------------------------------

    public FunctionCallingAgentService(@NotNull Project project) {
        this.project = project;
        this.rateLimiter = new ToolRateLimiter();
        this.toolProvider = new AgentToolProvider(project, rateLimiter);
        this.overrideModel = null;
    }

    /** Used by unit tests that mock the ToolRateLimiter. */
    @TestOnly
    FunctionCallingAgentService(Project project,
                                AgentToolProvider toolProvider,
                                ToolRateLimiter rateLimiter) {
        this.project = project;
        this.toolProvider = toolProvider;
        this.rateLimiter = rateLimiter;
        this.overrideModel = null;
    }

    /**
     * Used by IntelliJ Platform tests (BasePlatformTestCase) to inject a mock or
     * real StreamingChatModel without needing OllamAssistSettings to be initialised.
     */
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
     *
     * @param goal    natural-language objective
     * @param handler receives streaming tokens, tool events, and completion/error signals
     */
    public void execute(@NotNull String goal, @NotNull AgentStreamHandler handler) {
        if (disposed) {
            handler.onError(new IllegalStateException("FunctionCallingAgentService has been disposed"));
            return;
        }

        // Reset guards for this execution (AGENT_ARCH.md Rule 3, SI-6)
        rateLimiter.reset();
        toolProvider.resetAbort();
        AtomicInteger toolCallCount = new AtomicInteger(0);

        ReactAgent agent = buildAgent();

        agent.execute(goal)
                .onPartialResponse(handler::onToken)
                .onToolExecuted(te -> {
                    String name = te.request().name();
                    String args = te.request().arguments();
                    String result = te.result();

                    handler.onToolCall(name, args);
                    handler.onToolResult(name, result);

                    int count = toolCallCount.incrementAndGet();
                    if (count >= MAX_TOOL_CALLS_PER_EXECUTION) {
                        log.warn("Agent: MAX_TOOL_CALLS_PER_EXECUTION ({}) reached — aborting further tool calls",
                                MAX_TOOL_CALLS_PER_EXECUTION);
                        toolProvider.abort();
                    }
                })
                .onCompleteResponse(response -> handler.onComplete())
                .onError(error -> {
                    log.error("Agent execution failed: {}", error.getMessage(), error);
                    handler.onError(error);
                })
                .start();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private ReactAgent buildAgent() {
        ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(FunctionCallingAgentService.class.getClassLoader());

            StreamingChatModel model = overrideModel != null ? overrideModel : buildProductionModel();

            return AiServices.builder(ReactAgent.class)
                    .streamingChatModel(model)
                    .tools(toolProvider)
                    .build();
        } finally {
            Thread.currentThread().setContextClassLoader(originalCl);
        }
    }

    private StreamingChatModel buildProductionModel() {
        OllamaStreamingChatModel.OllamaStreamingChatModelBuilder builder =
                OllamaStreamingChatModel.builder()
                        .baseUrl(OllamAssistSettings.getInstance().getChatOllamaUrl())
                        .modelName(OllamAssistSettings.getInstance().getChatModelName())
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

    @Override
    public void dispose() {
        disposed = true;
    }
}
