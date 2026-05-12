package fr.baretto.ollamassist.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * One step in a Plan-then-Execute agent plan.
 *
 * <p>Supported tools:
 * <ul>
 *   <li>{@code writeFile}  — create a new file ({@link #path}, {@link #content})</li>
 *   <li>{@code editFile}   — line-based edit via JetBrains Document API:
 *       <ul>
 *         <li>{@code insertAfterLine} — insert {@link #code} after line {@link #line}</li>
 *         <li>{@code replaceLines}    — replace lines {@link #startLine}–{@link #endLine}
 *             with {@link #code}</li>
 *       </ul>
 *   </li>
 * </ul>
 */
public record AgentStep(
        String tool,
        @Nullable String path,

        // writeFile
        @Nullable String content,

        // editFile — operation type
        @Nullable String operation,   // "insertAfterLine" | "replaceLines"

        // editFile — insertAfterLine
        int line,

        // editFile — replaceLines
        int startLine,
        int endLine,

        // editFile — code to insert or replacement text
        @Nullable String code,

        // legacy / search tools
        @Nullable String query
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static AgentStep fromMap(Map<String, Object> map) {
        String tool = (String) map.get("tool");
        if (tool == null) return null;
        return new AgentStep(
                tool,
                (String) map.get("path"),
                (String) map.get("content"),
                (String) map.get("operation"),
                toInt(map.get("line")),
                toInt(map.get("startLine")),
                toInt(map.get("endLine")),
                (String) map.get("code"),
                (String) map.get("query")
        );
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) { try { return Integer.parseInt(s); } catch (Exception ignored) {} }
        return 0;
    }

    Map<String, Object> toParams() {
        Map<String, Object> params = new HashMap<>();
        if (path      != null) params.put("path",      path);
        if (content   != null) params.put("content",   content);
        if (operation != null) params.put("operation", operation);
        if (line      != 0)   params.put("line",       line);
        if (startLine != 0)   params.put("startLine",  startLine);
        if (endLine   != 0)   params.put("endLine",    endLine);
        if (code      != null) params.put("code",       code);
        if (query     != null) params.put("query",      query);
        return params;
    }

    String toArgsJson() {
        try {
            return MAPPER.writeValueAsString(toParams());
        } catch (JsonProcessingException e) {
            return "{\"tool\":\"" + tool + "\"}";
        }
    }

    String primaryArg() {
        if (path  != null) return path;
        if (query != null) return query;
        return "";
    }
}
