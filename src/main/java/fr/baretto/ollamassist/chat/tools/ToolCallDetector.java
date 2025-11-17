package fr.baretto.ollamassist.chat.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Detects tool calls in LLM text responses using multiple parsers.
 * This is a fallback mechanism for models that don't use native function calling.
 */
public class ToolCallDetector {

    private final List<ToolCallParser> parsers;

    public ToolCallDetector() {
        this.parsers = new ArrayList<>();
        this.parsers.add(new BracketCallParser());
    }

    /**
     * Attempts to detect a tool call in the given text using all registered parsers.
     *
     * @param text The text to analyze (max 50,000 characters processed)
     * @return Optional containing the detected tool call if found, empty otherwise
     */
    public Optional<DetectedToolCall> detect(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        // Limit text length to prevent performance issues
        String processedText = text.length() > 50_000 ? text.substring(0, 50_000) : text;

        // Try each parser in order until one succeeds
        for (ToolCallParser parser : parsers) {
            Optional<DetectedToolCall> result = parser.parse(processedText);
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    /**
     * Checks if the text contains any detectable tool call.
     *
     * @param text The text to check
     * @return true if a tool call is detected, false otherwise
     */
    public boolean containsToolCall(String text) {
        return detect(text).isPresent();
    }
}
