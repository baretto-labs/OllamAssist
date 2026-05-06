package fr.baretto.ollamassist.agent.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic mock of {@link StreamingChatModel} for platform tests.
 *
 * <p>Simulates the LLM side of a ReAct conversation: a sequence of steps,
 * each being either a tool call or a final text answer. The mock replays
 * this sequence across successive {@code chat()} invocations, matching how
 * {@code AiServices} re-invokes the model after each tool execution.
 *
 * <p>Example:
 * <pre>
 * MockReActModel model = MockReActModel.builder()
 *     .thenCall("writeFile", Map.of("path", "src/Foo.java", "content", "..."))
 *     .thenCall("editFile",  Map.of("path", "src/Foo.java", "search", "x", "replace", "y", "replaceAll", "false"))
 *     .thenAnswer("Done. Created and edited Foo.java.")
 *     .build();
 * </pre>
 */
public final class MockReActModel implements StreamingChatModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    sealed interface Step permits ToolCallStep, AnswerStep {}
    record ToolCallStep(String toolName, Map<String, String> args) implements Step {}
    record AnswerStep(String text) implements Step {}

    private final List<Step> steps;
    private final AtomicInteger cursor = new AtomicInteger(0);

    private MockReActModel(List<Step> steps) {
        this.steps = List.copyOf(steps);
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        int idx = cursor.getAndIncrement();
        if (idx >= steps.size()) {
            handler.onCompleteResponse(finalResponse("(no more steps defined)"));
            return;
        }
        Step step = steps.get(idx);
        switch (step) {
            case ToolCallStep tc -> handler.onCompleteResponse(toolCallResponse(tc));
            case AnswerStep ans -> {
                handler.onPartialResponse(ans.text());
                handler.onCompleteResponse(finalResponse(ans.text()));
            }
        }
    }

    // -------------------------------------------------------------------------

    private static ChatResponse toolCallResponse(ToolCallStep step) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.builder()
                        .toolExecutionRequests(List.of(
                                ToolExecutionRequest.builder()
                                        .id("mock-call-" + step.toolName())
                                        .name(step.toolName())
                                        .arguments(toJson(step.args()))
                                        .build()))
                        .build())
                .metadata(ChatResponseMetadata.builder()
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build())
                .build();
    }

    private static ChatResponse finalResponse(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .metadata(ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .build())
                .build();
    }

    private static String toJson(Map<String, String> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<Step> steps = new ArrayList<>();

        /** The model will emit a tool call for {@code toolName} with the given arguments. */
        public Builder thenCall(String toolName, Map<String, String> args) {
            steps.add(new ToolCallStep(toolName, args));
            return this;
        }

        /** The model will stream {@code text} as its final answer. */
        public Builder thenAnswer(String text) {
            steps.add(new AnswerStep(text));
            return this;
        }

        public MockReActModel build() {
            return new MockReActModel(steps);
        }
    }
}
