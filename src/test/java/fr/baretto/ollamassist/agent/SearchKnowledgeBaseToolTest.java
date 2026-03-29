package fr.baretto.ollamassist.agent;

import com.intellij.openapi.project.Project;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import fr.baretto.ollamassist.agent.tools.rag.SearchKnowledgeBaseTool;
import fr.baretto.ollamassist.chat.rag.LuceneEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SearchKnowledgeBaseToolTest {

    private Project mockProject;
    private LuceneEmbeddingStore<TextSegment> mockStore;
    private SearchKnowledgeBaseTool tool;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        mockProject = mock(Project.class);
        mockStore = mock(LuceneEmbeddingStore.class);
        when(mockProject.getService(LuceneEmbeddingStore.class)).thenReturn(mockStore);
        tool = new SearchKnowledgeBaseTool(mockProject);
    }

    @Test
    void toolId_isSearchKnowledge() {
        assertThat(tool.toolId()).isEqualTo("SEARCH_KNOWLEDGE");
    }

    @Test
    void missingQuery_returnsFailure() {
        ToolResult result = tool.execute(Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("'query' is required");
    }

    @Test
    void blankQuery_returnsFailure() {
        ToolResult result = tool.execute(Map.of("query", "  "));

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void storeNotAvailable_returnsFailure() {
        when(mockProject.getService(LuceneEmbeddingStore.class)).thenReturn(null);

        ToolResult result = tool.execute(Map.of("query", "interface Foo"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("not available");
    }

    @Test
    void noResults_returnsSuccessWithMessage() {
        when(mockStore.bm25Search("unknown term", 5)).thenReturn(List.of());

        ToolResult result = tool.execute(Map.of("query", "unknown term"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("No results");
    }

    @Test
    void withResults_returnsFormattedOutput() {
        TextSegment segment = TextSegment.from("class Foo { void bar() {} }");
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(0.9, "id1", null, segment);
        when(mockStore.bm25Search("Foo", 5)).thenReturn(List.of(match));

        ToolResult result = tool.execute(Map.of("query", "Foo"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("class Foo");
    }

    @Test
    void customTopK_passedToStore() {
        when(mockStore.bm25Search("test", 3)).thenReturn(List.of());

        tool.execute(Map.of("query", "test", "topK", 3));

        verify(mockStore).bm25Search("test", 3);
    }

    @Test
    void storeThrows_returnsFailure() {
        when(mockStore.bm25Search(any(), anyInt())).thenThrow(new RuntimeException("index locked"));

        ToolResult result = tool.execute(Map.of("query", "something"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("failed");
    }
}
