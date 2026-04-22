package fr.baretto.ollamassist.agent.tools.rag;

import com.intellij.openapi.project.Project;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import fr.baretto.ollamassist.chat.rag.LuceneEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Searches the project knowledge base (Lucene BM25 index) for relevant code segments.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code query}  — text to search for (required)</li>
 *   <li>{@code topK}   — max number of results (optional, default 5)</li>
 * </ul>
 */
@Slf4j
public final class SearchKnowledgeBaseTool implements AgentTool {

    private static final int DEFAULT_TOP_K = 5;

    private final Project project;

    public SearchKnowledgeBaseTool(Project project) {
        this.project = project;
    }

    @Override
    public String toolId() {
        return "SEARCH_KNOWLEDGE";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String query = (String) params.get("query");
        if (query == null || query.isBlank()) {
            return ToolResult.failure("Parameter 'query' is required");
        }

        int topK = DEFAULT_TOP_K;
        Object topKParam = params.get("topK");
        if (topKParam instanceof Number n) {
            topK = n.intValue();
        }

        LuceneEmbeddingStore<TextSegment> store = project.getService(LuceneEmbeddingStore.class);
        if (store == null) {
            return ToolResult.failure("Knowledge base is not available (RAG not initialised for this project)");
        }

        try {
            List<EmbeddingMatch<TextSegment>> matches = store.bm25Search(query, topK);
            if (matches.isEmpty()) {
                return ToolResult.success("No results found for: " + query);
            }

            String output = matches.stream()
                    .map(m -> {
                        String source = m.embedded().metadata().getString("source");
                        String text = m.embedded().text();
                        return (source != null ? "[" + source + "]\n" : "") + text;
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));

            log.debug("SEARCH_KNOWLEDGE: {} results for '{}'", matches.size(), query);
            return ToolResult.success(output);

        } catch (Exception e) {
            log.error("SEARCH_KNOWLEDGE failed for query: {}", query, e);
            return ToolResult.failure("Knowledge base search failed: " + e.getMessage());
        }
    }
}
