package fr.baretto.ollamassist.agent.tools.web;

import fr.baretto.ollamassist.agent.tools.AbstractQueryTool;
import fr.baretto.ollamassist.agent.tools.SearchEntry;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import fr.baretto.ollamassist.chat.rag.DuckDuckGoContentRetriever;
import fr.baretto.ollamassist.setting.RAGSettings;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Agent tool that searches the web via DuckDuckGo and returns ranked results.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code query} — search terms (required, validated by {@link AbstractQueryTool})</li>
 * </ul>
 *
 * <p>Tier: READ_ONLY — no approval required, no file mutations.
 *
 * <p>Respects the global web-search toggle in {@link RAGSettings}: when disabled,
 * returns a clear failure message instead of attempting the network call.
 *
 * <p>Unlike the chat-context {@code WebSearchTool} (which reformulates the query via the
 * LLM completion model), this tool calls {@link DuckDuckGoContentRetriever#searchRaw(String)}
 * directly — the PlannerAgent is expected to produce a precise query.
 */
@Slf4j
public final class WebSearchAgentTool extends AbstractQueryTool {

    private static final int MAX_RESULTS = 5;

    private final DuckDuckGoContentRetriever retriever;

    public WebSearchAgentTool() {
        this.retriever = new DuckDuckGoContentRetriever(MAX_RESULTS);
    }

    /** Test-only constructor — allows injecting a mock retriever. */
    @org.jetbrains.annotations.TestOnly
    WebSearchAgentTool(DuckDuckGoContentRetriever retriever) {
        this.retriever = retriever;
    }

    @Override
    public String toolId() {
        return "WEB_SEARCH";
    }

    @Override
    protected ToolResult preCheck() {
        try {
            if (!RAGSettings.getInstance().isWebSearchEnabled()) {
                return ToolResult.failure(
                        "Web search is disabled. Enable it in Settings → OllamAssist → RAG → Enable web search.");
            }
        } catch (Exception e) {
            log.debug("RAGSettings unavailable — allowing WEB_SEARCH (test context)");
        }
        return null;
    }

    @Override
    protected List<SearchEntry> search(String query) {
        log.debug("WEB_SEARCH: searching for '{}'", query);
        try {
            List<DuckDuckGoContentRetriever.SearchResult> raw = retriever.searchRaw(query);
            List<SearchEntry> entries = raw.stream()
                    .map(r -> new SearchEntry(r.url(), r.title(), r.snippet()))
                    .toList();
            log.debug("WEB_SEARCH: {} results for '{}'", entries.size(), query);
            return entries;
        } catch (Exception e) {
            log.warn("WEB_SEARCH failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    @Override
    protected String noResultsMessage(String query) {
        return "No web results found for: " + query
                + "\nTry rephrasing the query or use SEARCH_KNOWLEDGE to search the project index instead.";
    }
}
