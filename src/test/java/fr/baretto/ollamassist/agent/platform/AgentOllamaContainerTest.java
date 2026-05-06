package fr.baretto.ollamassist.agent.platform;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import fr.baretto.ollamassist.agent.AgentStreamHandler;
import fr.baretto.ollamassist.agent.AgentToolProvider;
import fr.baretto.ollamassist.agent.FunctionCallingAgentService;
import fr.baretto.ollamassist.agent.tools.ToolRateLimiter;
import org.junit.ClassRule;
import org.testcontainers.ollama.OllamaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end agent tests with a real Ollama instance running in a testcontainer.
 *
 * <p>Unlike unit tests with a mock model, these tests exercise the real LLM
 * behaviour: does the model actually use function calling to accomplish the task?
 * Does the ReAct loop adapt after receiving a tool observation? The tests are
 * intentionally non-deterministic — they verify outcomes, not exact tool sequences.
 *
 * <p>Model is configurable via the {@code platformTest.ollamaModel} Gradle property
 * (default: {@value #DEFAULT_MODEL}).  A model that supports function calling is
 * required; {@code qwen2.5:1.5b} is a good baseline (≈ 900 MB, fast enough for CI).
 *
 * <pre>
 * ./gradlew platformTest
 * ./gradlew platformTest -PplatformTest.ollamaModel=qwen2.5:7b
 * </pre>
 *
 * <p>The container is reused across tests in the class ({@code @ClassRule}) and
 * the model is pulled once. Subsequent runs reuse Docker's layer cache.
 */
public class AgentOllamaContainerTest extends AgentPlatformTestBase {

    static final String DEFAULT_MODEL = "qwen2.5:1.5b";

    /**
     * One container per test class, shared across all test methods.
     * The {@code withReuse(true)} option keeps the container alive between
     * Gradle runs so the model pull happens only once on the developer's machine.
     */
    @ClassRule
    public static final OllamaContainer OLLAMA = new OllamaContainer("ollama/ollama:latest")
            .withReuse(true);

    private static boolean modelPulled = false;

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        pullModelOnce();
    }

    private static synchronized void pullModelOnce() throws Exception {
        if (modelPulled) return;
        String model = resolveModel();
        System.out.println("[AgentOllamaContainerTest] Pulling model: " + model);
        var result = OLLAMA.execInContainer("ollama", "pull", model);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Failed to pull model '" + model + "': " + result.getStderr());
        }
        System.out.println("[AgentOllamaContainerTest] Model ready: " + model);
        modelPulled = true;
    }

    private static String resolveModel() {
        return System.getProperty("platformTest.ollamaModel", DEFAULT_MODEL);
    }

    // -------------------------------------------------------------------------
    // Helper: build service pointed at the testcontainer
    // -------------------------------------------------------------------------

    private FunctionCallingAgentService buildService() {
        OllamaStreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl(OLLAMA.getEndpoint())
                .modelName(resolveModel())
                .temperature(0.3)
                .timeout(Duration.ofMinutes(3))
                .build();

        AgentToolProvider toolProvider = new AgentToolProvider(getProject(), new ToolRateLimiter());
        return new FunctionCallingAgentService(getProject(), toolProvider, model);
    }

    /**
     * Runs the agent and blocks until completion or timeout.
     * Returns the accumulated final answer text.
     */
    private AgentRunResult runAgent(String goal, int timeoutSeconds) throws InterruptedException {
        FunctionCallingAgentService service = buildService();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        StringBuilder answer = new StringBuilder();
        List<String> toolCallNames = new ArrayList<>();

        service.execute(goal, new AgentStreamHandler() {
            public void onToken(String t)                { answer.append(t); }
            public void onToolCall(String n, String a)   { toolCallNames.add(n); System.out.println("[tool] " + n + " " + a); }
            public void onToolResult(String n, String r) { System.out.println("[obs]  " + n + ": " + r.substring(0, Math.min(80, r.length()))); }
            public void onComplete()                     { done.countDown(); }
            public void onError(Throwable e)             { error.set(e); done.countDown(); }
        });

        boolean completed = done.await(timeoutSeconds, TimeUnit.SECONDS);
        if (!completed) fail("Agent timed out after " + timeoutSeconds + "s for goal: " + goal);
        if (error.get() != null) fail("Agent error: " + error.get().getMessage());
        return new AgentRunResult(answer.toString(), toolCallNames);
    }

    record AgentRunResult(String answer, List<String> toolCalls) {}

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Verifies the model uses the writeFile tool to create a file.
     * The test is intentionally lenient on content — only the presence of the
     * file and a coherent answer are asserted.
     */
    public void testAgent_createSimpleFile_fileExistsAfterExecution() throws Exception {
        AgentRunResult result = runAgent(
                "Create a file named Greeter.java containing a simple Java class " +
                "with a greet(String name) method that returns 'Hello, <name>!'", 180);

        assertThat(result.answer()).isNotBlank();
        assertThat(result.toolCalls()).contains("writeFile");

        VirtualFile file = LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(getProject().getBasePath() + "/Greeter.java");
        assertNotNull("writeFile was called but Greeter.java does not exist in the VFS", file);
        String content = new String(file.contentsToByteArray());
        assertThat(content).containsIgnoringCase("greet");
    }

    /**
     * Verifies the model can search the workspace before editing —
     * a two-step ReAct sequence: searchWorkspace → editFile.
     */
    public void testAgent_searchThenEdit_multiStepReAct() throws Exception {
        // Seed a file the agent can find and edit
        myFixture.addFileToProject("src/Calculator.java",
                "public class Calculator {\n" +
                "    public int add(int a, int b) { return 0; }\n" +
                "}");

        AgentRunResult result = runAgent(
                "Find the Calculator class in the workspace and fix the add() method " +
                "so it returns a + b instead of 0.", 180);

        assertThat(result.answer()).isNotBlank();

        // Either searchWorkspace or editFile (or both) should have been called
        assertThat(result.toolCalls()).containsAnyOf("searchWorkspace", "editFile");

        // If editFile was called, the file should be correct
        if (result.toolCalls().contains("editFile")) {
            assertThat(readProjectFile("src/Calculator.java")).contains("a + b");
        }
    }

    /**
     * Verifies the approval flow in a real ReAct run: the agent proposes a file
     * creation, the user rejects it, and the agent receives the rejection as an
     * observation and produces a coherent final response.
     */
    public void testAgent_userRejectsWrite_agentReceivesObservationAndResponds() throws Exception {
        setAutoApprove(false);

        AgentRunResult result = runAgent(
                "Create a file named Secret.java with a simple class.", 180);

        // File must NOT exist
        VirtualFile file = LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(getProject().getBasePath() + "/Secret.java");
        assertNull("File should not be created when user rejects", file);

        // Agent must have produced a final answer despite the rejection
        assertThat(result.answer()).isNotBlank();
    }

    /**
     * Smoke test: agent produces a final answer without crashing.
     * Verifies end-to-end connectivity with the real model.
     */
    public void testAgent_smokeTest_producesAnswer() throws Exception {
        AgentRunResult result = runAgent(
                "List the Java files in this project workspace.", 120);

        assertThat(result.answer()).isNotBlank();
        // searchWorkspace is a natural choice for this goal
        System.out.println("[smoke] Tool calls: " + result.toolCalls());
        System.out.println("[smoke] Answer: " + result.answer().substring(0, Math.min(200, result.answer().length())));
    }
}
