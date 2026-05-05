package fr.baretto.ollamassist.agent.tools.rag;

import com.intellij.openapi.project.Project;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import fr.baretto.ollamassist.agent.tools.AbstractQueryTool;
import fr.baretto.ollamassist.agent.tools.SearchEntry;
import fr.baretto.ollamassist.chat.rag.LuceneEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Searches the project knowledge base (Lucene BM25 index) for relevant code segments.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code query} — text to search for (required, validated by {@link AbstractQueryTool})</li>
 *   <li>{@code topK}  — max number of results (optional, default 5)</li>
 * </ul>
 */
@Slf4j
public final class SearchKnowledgeBaseTool extends AbstractQueryTool {

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
    protected List<SearchEntry> search(String query) {
        LuceneEmbeddingStore<TextSegment> store = project.getService(LuceneEmbeddingStore.class);
        if (store == null) {
            throw new IllegalStateException(
                    "Knowledge base not available (RAG not initialised for this project)");
        }
        // Let exceptions propagate — AbstractQueryTool.execute() converts them to ToolResult.failure
        List<EmbeddingMatch<TextSegment>> matches = store.bm25Search(query, DEFAULT_TOP_K);
        List<SearchEntry> entries = matches.stream()
                .map(m -> new SearchEntry(
                        nullToEmpty(m.embedded().metadata().getString("source")),
                        "",
                        m.embedded().text()))
                .toList();
        log.debug("SEARCH_KNOWLEDGE: {} results for '{}'", entries.size(), query);
        return entries;
    }

    @Override
    protected String noResultsMessage(String query) {
        return "No results found in the knowledge base for: " + query;
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
