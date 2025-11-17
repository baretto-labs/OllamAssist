package fr.baretto.ollamassist.chat.tools;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Represents a tool call detected in LLM text response.
 * Used when the model generates tool call instructions as text instead of using native function calling.
 */
@Getter
@Builder
public class DetectedToolCall {

    /**
     * Name of the tool to be called (e.g., "CreateFile")
     */
    private final String toolName;

    /**
     * Arguments extracted from the text, mapped by parameter name
     */
    private final Map<String, String> arguments;

    /**
     * Original text that was parsed to detect this tool call
     */
    private final String originalText;

    /**
     * Parser that detected this call (for debugging purposes)
     */
    private final String detectedBy;
}
