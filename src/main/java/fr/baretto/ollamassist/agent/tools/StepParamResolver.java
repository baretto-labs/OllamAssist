package fr.baretto.ollamassist.agent.tools;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves dynamic placeholders in step params at execution time.
 *
 * <p>Supported placeholders (placed as param values in the plan JSON):
 * <ul>
 *   <li>{@code {{prev_output}}}           — replaced with the full output of the previous step</li>
 *   <li>{@code {{prev_output_first_line}}} — replaced with the first non-blank line of the previous
 *       step's output (useful after FILE_FIND to get the first matched path)</li>
 * </ul>
 *
 * <p>Non-string params and params containing no placeholder are returned as-is.
 * If {@code previousOutput} is null or blank and a placeholder is present, an
 * {@link UnresolvablePlaceholderException} is thrown so the caller can report a clear error.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StepParamResolver {

    public static final String PREV_OUTPUT = "{{prev_output}}";
    public static final String PREV_OUTPUT_FIRST_LINE = "{{prev_output_first_line}}";

    /**
     * Returns a resolved copy of {@code params} with placeholders replaced.
     * The returned map is unmodifiable.
     *
     * @throws UnresolvablePlaceholderException if a placeholder is present but the previous output
     *                                          is empty or blank (nothing to substitute)
     */
    public static Map<String, Object> resolve(Map<String, Object> params, String previousOutput) {
        if (params.isEmpty()) return params;

        List<String> paramKeysWithPlaceholder = params.entrySet().stream()
                .filter(e -> e.getValue() instanceof String s
                        && (s.contains(PREV_OUTPUT) || s.contains(PREV_OUTPUT_FIRST_LINE)))
                .map(Map.Entry::getKey)
                .toList();

        if (paramKeysWithPlaceholder.isEmpty()) return params;

        if (previousOutput == null || previousOutput.isBlank()) {
            throw new UnresolvablePlaceholderException(
                    "Params " + paramKeysWithPlaceholder + " contain a placeholder but the previous step produced no output. "
                    + "Make sure the preceding step (e.g. FILE_FIND) executed successfully before using {{prev_output_first_line}}.");
        }

        String firstLine = previousOutput.lines()
                .map(String::trim)
                .filter(l -> !l.isBlank())
                .findFirst()
                .orElse("");

        if (firstLine.isEmpty() && paramKeysWithPlaceholder.stream().anyMatch(
                k -> ((String) params.get(k)).contains(PREV_OUTPUT_FIRST_LINE))) {
            throw new UnresolvablePlaceholderException(
                    "Params " + paramKeysWithPlaceholder + " use {{prev_output_first_line}} but the previous step output contains no non-blank lines: \""
                    + previousOutput + "\"");
        }

        // Strip placeholder tokens from the resolved values to prevent double substitution
        // (e.g., a file path that literally contains "{{prev_output}}" would otherwise be re-resolved)
        String safeFirstLine = firstLine.replace(PREV_OUTPUT_FIRST_LINE, "").replace(PREV_OUTPUT, "");
        String safePreviousOutput = previousOutput.replace(PREV_OUTPUT_FIRST_LINE, "").replace(PREV_OUTPUT, "");

        Map<String, Object> resolved = new HashMap<>(params);
        resolved.replaceAll((key, value) -> {
            if (value instanceof String s) {
                return s.replace(PREV_OUTPUT_FIRST_LINE, safeFirstLine)
                        .replace(PREV_OUTPUT, safePreviousOutput);
            }
            return value;
        });
        return Collections.unmodifiableMap(resolved);
    }

    /** Thrown when a placeholder cannot be resolved due to missing or empty previous output. */
    public static final class UnresolvablePlaceholderException extends RuntimeException {
        public UnresolvablePlaceholderException(String message) {
            super(message);
        }
    }
}
