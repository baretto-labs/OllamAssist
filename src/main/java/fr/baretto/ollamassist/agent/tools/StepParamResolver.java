package fr.baretto.ollamassist.agent.tools;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves dynamic placeholders in step params at execution time.
 *
 * <p>Supported placeholders:
 * <ul>
 *   <li>{@code {{prev_output}}}           — full output of the previous step</li>
 *   <li>{@code {{prev_output_first_line}}} — first non-blank line of the previous step's output</li>
 *   <li>{@code {{var.NAME}}}               — output of the step that declared {@code "outputVar": "NAME"}</li>
 * </ul>
 *
 * <p>Non-string params and params with no placeholder are returned as-is.
 * {@link UnresolvablePlaceholderException} is thrown when a placeholder is present but
 * the required value is absent.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StepParamResolver {

    public static final String PREV_OUTPUT = "{{prev_output}}";
    public static final String PREV_OUTPUT_FIRST_LINE = "{{prev_output_first_line}}";
    /** Prefix for named-variable references: {@code {{var.NAME}}} */
    static final String VAR_PREFIX = "{{var.";

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{var\\.([^}]+)}}");

    /**
     * Resolves placeholders in {@code params} using the previous step output only.
     * Named variables ({@code {{var.NAME}}}) are not substituted by this overload.
     */
    public static Map<String, Object> resolve(Map<String, Object> params, String previousOutput) {
        return resolve(params, previousOutput, Map.of());
    }

    /**
     * Returns a resolved copy of {@code params} with all supported placeholders substituted.
     *
     * @param params         the raw step params from the plan
     * @param previousOutput output of the immediately preceding step (may be null/blank)
     * @param variables      named outputs from earlier steps (name → value)
     * @throws UnresolvablePlaceholderException if a placeholder is present but cannot be resolved
     */
    public static Map<String, Object> resolve(Map<String, Object> params, String previousOutput,
                                              Map<String, String> variables) {
        if (params.isEmpty()) return params;

        boolean hasAnyPlaceholder = params.values().stream()
                .anyMatch(v -> v instanceof String s && containsPlaceholder(s));

        if (!hasAnyPlaceholder) return params;

        // Validate prev_output placeholders
        List<String> prevOutputKeys = params.entrySet().stream()
                .filter(e -> e.getValue() instanceof String s
                        && (s.contains(PREV_OUTPUT) || s.contains(PREV_OUTPUT_FIRST_LINE)))
                .map(Map.Entry::getKey)
                .toList();

        if (!prevOutputKeys.isEmpty() && (previousOutput == null || previousOutput.isBlank())) {
            throw new UnresolvablePlaceholderException(
                    "Params " + prevOutputKeys + " contain a placeholder but the previous step produced no output. "
                    + "Make sure the preceding step (e.g. FILE_FIND) executed successfully before using {{prev_output_first_line}}.");
        }

        String firstLine = "";
        if (previousOutput != null) {
            firstLine = previousOutput.lines()
                    .map(String::trim)
                    .filter(l -> !l.isBlank())
                    .findFirst()
                    .orElse("");
        }

        if (firstLine.isEmpty() && prevOutputKeys.stream().anyMatch(
                k -> ((String) params.get(k)).contains(PREV_OUTPUT_FIRST_LINE))) {
            throw new UnresolvablePlaceholderException(
                    "Params " + prevOutputKeys + " use {{prev_output_first_line}} but the previous step output "
                    + "contains no non-blank lines: \"" + previousOutput + "\"");
        }

        // Sanitize to prevent double substitution
        String safeFirstLine = firstLine.replace(PREV_OUTPUT_FIRST_LINE, "").replace(PREV_OUTPUT, "");
        String safePrevOutput = previousOutput != null
                ? previousOutput.replace(PREV_OUTPUT_FIRST_LINE, "").replace(PREV_OUTPUT, "")
                : "";

        Map<String, Object> resolved = new HashMap<>(params);
        resolved.replaceAll((key, value) -> {
            if (!(value instanceof String s)) return value;
            // Resolution order matters:
            //   1. {{prev_output_first_line}} / {{prev_output}} are substituted first,
            //      using sanitized values that have their own placeholders stripped — no re-entry.
            //   2. {{var.NAME}} is substituted second via a single-pass Matcher — the replacement
            //      text is appended to a StringBuffer without re-scanning, so a varValue that
            //      contains "{{prev_output}}" or "{{var.OTHER}}" will be embedded as literal text.
            // This single-pass design prevents double-substitution by construction.
            s = s.replace(PREV_OUTPUT_FIRST_LINE, safeFirstLine).replace(PREV_OUTPUT, safePrevOutput);
            // Substitute {{var.NAME}} references
            Matcher m = VAR_PATTERN.matcher(s);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String varName = m.group(1);
                String varValue = variables.getOrDefault(varName, null);
                if (varValue == null) {
                    throw new UnresolvablePlaceholderException(
                            "Param '" + key + "' references {{var." + varName + "}} but no step has declared "
                            + "\"outputVar\": \"" + varName + "\" yet. Available vars: " + variables.keySet());
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(varValue));
            }
            m.appendTail(sb);
            return sb.toString();
        });
        return Collections.unmodifiableMap(resolved);
    }

    private static boolean containsPlaceholder(String s) {
        return s.contains(PREV_OUTPUT) || s.contains(PREV_OUTPUT_FIRST_LINE) || s.contains(VAR_PREFIX);
    }

    /** Thrown when a placeholder cannot be resolved due to missing or empty data. */
    public static final class UnresolvablePlaceholderException extends RuntimeException {
        public UnresolvablePlaceholderException(String message) {
            super(message);
        }
    }
}
