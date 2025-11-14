package fr.baretto.ollamassist.chat.tools;

import java.util.Optional;

/**
 * Interface for parsing tool calls from LLM text responses.
 * Different implementations can handle different formats (e.g., "[CALL CreateFile(...)]", "createFile(...)", etc.)
 */
public interface ToolCallParser {

    /**
     * Attempts to parse a tool call from the given text.
     *
     * @param text The text to parse
     * @return Optional containing the detected tool call if found, empty otherwise
     */
    Optional<DetectedToolCall> parse(String text);

    /**
     * Returns the name of this parser (for debugging/logging)
     */
    String getParserName();
}
