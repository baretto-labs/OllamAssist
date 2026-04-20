package fr.baretto.ollamassist.agent;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Sanitizes tool outputs before they are inserted into LLM prompts.
 *
 * <h2>Prompt injection defence</h2>
 * <p>Tool outputs are wrapped in non-XML bracket delimiters ({@code <<TOOL_DATA>>} /
 * {@code <</TOOL_DATA>>}) rather than XML tags. XML tags are vulnerable because a
 * file containing the literal string {@code </tool_output>} would close the boundary,
 * letting subsequent content be interpreted as instructions. The chosen delimiters
 * are uncommon in source code and are auto-escaped if they appear in the content.
 *
 * <h2>Other controls</h2>
 * <ul>
 *   <li>Outputs truncated to {@value #MAX_OUTPUT_CHARS} characters.</li>
 *   <li>ASCII control characters (except \n, \r, \t) removed.</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PromptSanitizer {

    /** Maximum characters kept per individual step output in prompts. */
    static final int MAX_OUTPUT_CHARS = 2_000;

    static final String DELIMITER_START = "<<TOOL_DATA>>";
    static final String DELIMITER_END   = "<</TOOL_DATA>>";
    /** Replacement used when the delimiter itself appears inside content. */
    private static final String ESCAPED_START = "<<TOOL_DATA_ESCAPED>>";
    private static final String ESCAPED_END   = "<</TOOL_DATA_ESCAPED>>";

    /**
     * Wraps {@code rawOutput} in bracket delimiters and truncates it if it
     * exceeds {@link #MAX_OUTPUT_CHARS} characters.
     *
     * <p>If the content itself contains the delimiter strings they are replaced
     * with their escaped variants, preventing boundary confusion.
     */
    public static String sanitize(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return DELIMITER_START + "\n(empty)\n" + DELIMITER_END;
        }
        String cleaned = stripControlChars(rawOutput);
        String escaped = escapeDelimiters(cleaned);
        String truncated = truncate(escaped);
        return DELIMITER_START + "\n" + truncated + "\n" + DELIMITER_END;
    }

    /**
     * Sanitizes a goal or user-provided string (shorter limit, no wrapping).
     * Used for the goal field in the critic prompt.
     */
    public static String sanitizeGoal(String goal) {
        if (goal == null) return "(no goal)";
        String cleaned = stripControlChars(goal);
        return truncate(cleaned, 500);
    }

    // -------------------------------------------------------------------------

    private static String escapeDelimiters(String s) {
        return s.replace(DELIMITER_START, ESCAPED_START)
                .replace(DELIMITER_END, ESCAPED_END);
    }

    private static String stripControlChars(String s) {
        // Remove null bytes, BOM, and other non-printable ASCII control chars
        // but keep \n, \r, \t which are legitimate in code
        s = s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        // Strip Unicode bidirectional control characters (RTLO, LRO, PDF, etc.) and
        // zero-width characters (ZWSP, BOM). These are used in bidi prompt injection
        // attacks where the display order of text differs from its byte order, making
        // injected instructions visually invisible or disguised in the IDE.
        s = s.replaceAll("[\\u200B\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]", "");
        return s;
    }

    private static String truncate(String s) {
        return truncate(s, MAX_OUTPUT_CHARS);
    }

    static String truncate(String s, int maxChars) {
        if (s.length() <= maxChars) return s;
        int half = maxChars / 2;
        return s.substring(0, half)
                + "\n... [truncated " + (s.length() - maxChars) + " chars] ...\n"
                + s.substring(s.length() - half);
    }
}
