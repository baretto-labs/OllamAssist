package fr.baretto.ollamassist.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses text-based tool call attempts from model output.
 *
 * <p>Some local models (qwen3 via Ollama streaming) intermittently fall back to emitting
 * tool calls as JSON text — {@code {"name":"...","parameters":{...}}} — instead of using
 * the native function-calling API.  This parser detects and normalises those patterns so
 * the ReAct loop can execute them as real tool calls.
 */
@Slf4j
public final class TextToolCallParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TextToolCallParser() {}

    /**
     * A tool call found in a text response.
     *
     * @param name     tool name (e.g. "writeFile")
     * @param argsJson arguments as a JSON object string, for building a ToolExecutionRequest
     * @param args     arguments as a flat string map, for dispatch
     * @param rawJson  the full matched JSON substring in the original text
     */
    public record ParsedToolCall(String name, String argsJson, Map<String, String> args, String rawJson) {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Extracts all JSON-text tool calls from a model response.
     * Handles both {@code "parameters"} and {@code "arguments"} as the args wrapper key.
     */
    public static List<ParsedToolCall> parse(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<ParsedToolCall> result = new ArrayList<>();

        int i = 0;
        while (i < text.length()) {
            int start = text.indexOf('{', i);
            if (start < 0) break;

            int end = findJsonEnd(text, start);
            if (end < 0) {
                i = start + 1;
                continue;
            }

            String raw = text.substring(start, end + 1);
            ParsedToolCall call = tryParseToolCall(raw);
            if (call != null) {
                result.add(call);
            }
            i = end + 1;
        }
        return result;
    }

    /**
     * Returns everything before the first JSON tool call — the model's reasoning text
     * that should be preserved in the conversation history AiMessage.
     */
    public static String extractReasoningText(String fullText, List<ParsedToolCall> calls) {
        if (calls.isEmpty() || fullText == null) return fullText;
        int firstStart = fullText.indexOf(calls.get(0).rawJson());
        return firstStart > 0 ? fullText.substring(0, firstStart).stripTrailing() : "";
    }

    /**
     * Parses a JSON arguments string (from a native {@code ToolExecutionRequest}) into a flat
     * string map suitable for {@link FunctionCallingAgentService} dispatch.
     */
    public static Map<String, String> parseArgsJson(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) return Map.of();
        try {
            return flattenToStrings(MAPPER.readTree(argsJson));
        } catch (Exception e) {
            log.debug("Failed to parse tool args JSON: {}", argsJson);
            return Map.of();
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static ParsedToolCall tryParseToolCall(String raw) {
        try {
            JsonNode node = MAPPER.readTree(raw);
            if (!node.isObject() || !node.has("name")) return null;

            String name = node.get("name").asText();
            JsonNode params = node.has("parameters") ? node.get("parameters")
                            : node.has("arguments")  ? node.get("arguments") : null;
            if (params == null || !params.isObject()) return null;

            return new ParsedToolCall(name, params.toString(), flattenToStrings(params), raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, String> flattenToStrings(JsonNode object) {
        Map<String, String> map = new LinkedHashMap<>();
        object.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText()));
        return map;
    }

    /**
     * Finds the index of the closing {@code }} that balances the {@code {} at {@code start}.
     * Handles nested objects/arrays and string escape sequences.
     *
     * @return index of the closing brace, or {@code -1} if unbalanced
     */
    static int findJsonEnd(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0 && c == '}') return i;
            }
        }
        return -1;
    }
}
