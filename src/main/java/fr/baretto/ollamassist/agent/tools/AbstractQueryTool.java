package fr.baretto.ollamassist.agent.tools;

import java.util.List;
import java.util.Map;

/**
 * Base class for agent tools that accept a text query and return ranked results.
 *
 * <p>Handles the shared concerns so each concrete tool implements only its search logic:
 * <ul>
 *   <li>Query param validation (null / blank → failure)</li>
 *   <li>Empty-result handling</li>
 *   <li>Consistent output formatting (source header + body, separated by {@code ---})</li>
 * </ul>
 *
 * <p>Concrete implementations: {@link fr.baretto.ollamassist.agent.tools.rag.SearchKnowledgeBaseTool},
 * {@link fr.baretto.ollamassist.agent.tools.web.WebSearchAgentTool}.
 */
public abstract class AbstractQueryTool implements AgentTool {

    /** Maximum chars per result body before truncation. */
    private static final int MAX_BODY_CHARS = 1_000;
    private static final String SEPARATOR = "\n\n---\n\n";

    /**
     * Execute a search for {@code query} and return ordered results.
     * Implementations must not throw — return an empty list on failure and log the cause.
     */
    protected abstract List<SearchEntry> search(String query);

    /** Optional hook: called when the search returns no results. Default returns a generic message. */
    protected String noResultsMessage(String query) {
        return "No results found for: " + query;
    }

    /**
     * Optional pre-check called before executing the search.
     * Return a non-null {@link ToolResult} to short-circuit execution (e.g. feature disabled).
     * Return {@code null} to proceed normally.
     */
    protected ToolResult preCheck() {
        return null;
    }

    @Override
    public final ToolResult execute(Map<String, Object> params) {
        ToolResult guard = preCheck();
        if (guard != null) return guard;

        String query = (String) params.get("query");
        if (query == null || query.isBlank()) {
            return ToolResult.failure("Parameter 'query' is required");
        }

        List<SearchEntry> results;
        try {
            results = search(query.trim());
        } catch (Exception e) {
            return ToolResult.failure(toolId() + " search failed: " + e.getMessage());
        }
        if (results == null || results.isEmpty()) {
            return ToolResult.success(noResultsMessage(query));
        }

        StringBuilder sb = new StringBuilder();
        for (SearchEntry entry : results) {
            if (!entry.source().isBlank()) {
                sb.append("[").append(entry.source()).append("]\n");
            }
            if (!entry.title().isBlank()) {
                sb.append(entry.title()).append("\n");
            }
            sb.append(truncate(entry.body()));
            sb.append(SEPARATOR);
        }
        // Remove trailing separator
        String output = sb.toString();
        if (output.endsWith(SEPARATOR)) {
            output = output.substring(0, output.length() - SEPARATOR.length());
        }
        return ToolResult.success(output.stripTrailing());
    }

    private static String truncate(String text) {
        if (text == null) return "";
        if (text.length() <= MAX_BODY_CHARS) return text;
        int head = (int) (MAX_BODY_CHARS * 0.7);
        int tail = MAX_BODY_CHARS - head;
        return text.substring(0, head)
                + "\n... [" + (text.length() - MAX_BODY_CHARS) + " chars omitted] ...\n"
                + text.substring(text.length() - tail);
    }
}
