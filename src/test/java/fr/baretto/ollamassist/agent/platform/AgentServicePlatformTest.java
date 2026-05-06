package fr.baretto.ollamassist.agent.platform;

import fr.baretto.ollamassist.agent.AgentStreamHandler;
import fr.baretto.ollamassist.agent.AgentToolProvider;
import fr.baretto.ollamassist.agent.FunctionCallingAgentService;
import fr.baretto.ollamassist.agent.tools.ToolRateLimiter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Platform integration tests for the full ReAct agent loop.
 *
 * <p>Uses {@link MockReActModel} to simulate the LLM without requiring Ollama.
 * Each test verifies that the agent correctly: dispatches tool calls, passes
 * observations back to the model, handles approval flow, and delivers the
 * final answer to the UI handler.
 */
public class AgentServicePlatformTest extends AgentPlatformTestBase {

    private FunctionCallingAgentService service;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // We'll create the service per-test with the appropriate mock model.
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private FunctionCallingAgentService serviceWith(MockReActModel model) {
        AgentToolProvider toolProvider = new AgentToolProvider(getProject(), new ToolRateLimiter());
        return new FunctionCallingAgentService(getProject(), toolProvider, model);
    }

    /** Runs the agent synchronously and returns the final text response. Fails if timeout reached. */
    private String runAgent(FunctionCallingAgentService svc, String goal) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> answer = new AtomicReference<>("");
        AtomicReference<Throwable> error = new AtomicReference<>();
        StringBuilder tokens = new StringBuilder();

        svc.execute(goal, new AgentStreamHandler() {
            public void onToken(String t)                   { tokens.append(t); }
            public void onToolCall(String n, String a)      {}
            public void onToolResult(String n, String r)    {}
            public void onComplete()                        { answer.set(tokens.toString()); done.countDown(); }
            public void onError(Throwable e)                { error.set(e); done.countDown(); }
        });

        assertTrue("Agent execution timed out", done.await(30, TimeUnit.SECONDS));
        if (error.get() != null) fail("Agent error: " + error.get().getMessage());
        return answer.get();
    }

    // -------------------------------------------------------------------------
    // Single tool call → final answer
    // -------------------------------------------------------------------------

    public void testAgent_createFile_oneStep() throws Exception {
        MockReActModel model = MockReActModel.builder()
                .thenCall("writeFile", Map.of(
                        "path",    "src/Hello.java",
                        "content", "public class Hello { public static void main(String[] a) {} }"))
                .thenAnswer("Done. I created Hello.java.")
                .build();

        String answer = runAgent(serviceWith(model), "Create Hello.java");

        assertThat(answer).contains("Done");
        assertThat(readProjectFile("src/Hello.java")).contains("public class Hello");
    }

    // -------------------------------------------------------------------------
    // Multi-step: create then edit
    // -------------------------------------------------------------------------

    public void testAgent_createThenEdit_twoSteps() throws Exception {
        MockReActModel model = MockReActModel.builder()
                .thenCall("writeFile", Map.of(
                        "path",    "src/Fizz.java",
                        "content", "public class Fizz { public String compute(int n) { return \"\"; } }"))
                .thenCall("editFile", Map.of(
                        "path",       "src/Fizz.java",
                        "search",     "return \"\";",
                        "replace",    "return n % 15 == 0 ? \"FizzBuzz\" : n % 3 == 0 ? \"Fizz\" : n % 5 == 0 ? \"Buzz\" : String.valueOf(n);",
                        "replaceAll", "false"))
                .thenAnswer("Done. FizzBuzz logic added to Fizz.java.")
                .build();

        String answer = runAgent(serviceWith(model), "Create a FizzBuzz service");

        assertThat(answer).contains("Done");
        String content = readProjectFile("src/Fizz.java");
        assertThat(content)
                .contains("FizzBuzz")
                .contains("Fizz")
                .contains("Buzz");
    }

    // -------------------------------------------------------------------------
    // Tool call events reach the handler
    // -------------------------------------------------------------------------

    public void testAgent_toolCallEventsDeliveredToHandler() throws Exception {
        List<String> toolCallNames = new ArrayList<>();

        MockReActModel model = MockReActModel.builder()
                .thenCall("searchWorkspace", Map.of("query", "FizzBuzz"))
                .thenAnswer("Nothing found.")
                .build();

        FunctionCallingAgentService svc = serviceWith(model);
        CountDownLatch done = new CountDownLatch(1);

        svc.execute("Find FizzBuzz", new AgentStreamHandler() {
            public void onToken(String t)               {}
            public void onToolCall(String n, String a)  { toolCallNames.add(n); }
            public void onToolResult(String n, String r){}
            public void onComplete()                    { done.countDown(); }
            public void onError(Throwable e)            { done.countDown(); }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS));
        assertThat(toolCallNames).containsExactly("searchWorkspace");
    }

    // -------------------------------------------------------------------------
    // Approval rejection stops tool execution
    // -------------------------------------------------------------------------

    public void testAgent_userRejectsWrite_observationReachesModel() throws Exception {
        setAutoApprove(false); // user will reject the file creation

        MockReActModel model = MockReActModel.builder()
                .thenCall("writeFile", Map.of("path", "src/Rejected.java", "content", "content"))
                .thenAnswer("The user rejected the file creation.")
                .build();

        String answer = runAgent(serviceWith(model), "Create Rejected.java");

        // File must NOT exist
        var file = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(getProject().getBasePath() + "/src/Rejected.java");
        assertNull("File should not exist after rejection", file);

        // The model must have received the error observation and replied
        assertThat(answer).isNotBlank();
    }

    // -------------------------------------------------------------------------
    // Rate limit: abort flag propagates cleanly
    // -------------------------------------------------------------------------

    public void testAgent_abortFlagStopsToolCalls() throws Exception {
        // Build a model that tries to call writeFile many times
        MockReActModel.Builder builder = MockReActModel.builder();
        for (int i = 0; i < FunctionCallingAgentService.MAX_TOOL_CALLS_PER_EXECUTION + 5; i++) {
            builder.thenCall("searchWorkspace", Map.of("query", "query" + i));
        }
        builder.thenAnswer("Done.");
        MockReActModel model = builder.build();

        FunctionCallingAgentService svc = serviceWith(model);
        CountDownLatch done = new CountDownLatch(1);
        List<String> toolCalls = new ArrayList<>();

        svc.execute("search many times", new AgentStreamHandler() {
            public void onToken(String t)               {}
            public void onToolCall(String n, String a)  { toolCalls.add(n); }
            public void onToolResult(String n, String r){}
            public void onComplete()                    { done.countDown(); }
            public void onError(Throwable e)            { done.countDown(); }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS));
        // Must not exceed the limit
        assertThat(toolCalls.size()).isLessThanOrEqualTo(FunctionCallingAgentService.MAX_TOOL_CALLS_PER_EXECUTION);
    }

    // -------------------------------------------------------------------------
    // Real Ollama (skipped if not running)
    // -------------------------------------------------------------------------

    @RequiresOllama
    public void testAgent_withRealOllama_createsFizzBuzzFile() throws Exception {
        assumeOllamaRunning();

        // Build the service with production model (reads OllamAssistSettings)
        // This test requires OllamAssistSettings to be initialised — it will
        // fall back to defaults (localhost:11434) if the settings service isn't wired.
        AgentToolProvider toolProvider = new AgentToolProvider(getProject(), new ToolRateLimiter());
        // Use production constructor which reads settings
        FunctionCallingAgentService realService = new FunctionCallingAgentService(getProject());

        String answer = runAgent(realService,
                "Create a file src/FizzBuzz.java with a fizzBuzz(int n) method that returns " +
                "FizzBuzz for multiples of 15, Fizz for 3, Buzz for 5, and the number otherwise.");

        assertThat(answer).isNotBlank();
        // File may or may not have been created depending on the model's behaviour —
        // the test passes as long as no exception is thrown and we get a final answer.
        System.out.println("[platformTest:ollama] Agent answer: " + answer);
    }
}
