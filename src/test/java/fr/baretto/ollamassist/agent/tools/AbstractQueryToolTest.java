package fr.baretto.ollamassist.agent.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractQueryToolTest {

    // Minimal concrete implementation for testing
    private static AbstractQueryTool tool(List<SearchEntry> results) {
        return new AbstractQueryTool() {
            @Override public String toolId() { return "TEST_QUERY"; }
            @Override protected List<SearchEntry> search(String query) { return results; }
        };
    }

    @Test
    void missingQuery_returnsFailure() {
        ToolResult r = tool(List.of()).execute(Map.of());
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).contains("'query' is required");
    }

    @Test
    void blankQuery_returnsFailure() {
        ToolResult r = tool(List.of()).execute(Map.of("query", "   "));
        assertThat(r.isSuccess()).isFalse();
    }

    @Test
    void noResults_returnsSuccessWithMessage() {
        ToolResult r = tool(List.of()).execute(Map.of("query", "foo"));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getOutput()).contains("No results");
    }

    @Test
    void singleResult_formattedCorrectly() {
        ToolResult r = tool(List.of(new SearchEntry("src/Foo.java", "Foo", "class Foo {}")))
                .execute(Map.of("query", "foo"));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getOutput()).contains("[src/Foo.java]");
        assertThat(r.getOutput()).contains("Foo");
        assertThat(r.getOutput()).contains("class Foo {}");
    }

    @Test
    void multipleResults_separatedByDashes() {
        ToolResult r = tool(List.of(
                new SearchEntry("a.java", "", "body A"),
                new SearchEntry("b.java", "", "body B")))
                .execute(Map.of("query", "q"));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getOutput()).contains("body A");
        assertThat(r.getOutput()).contains("body B");
        assertThat(r.getOutput()).contains("---");
    }

    @Test
    void longBody_truncatedWithHeadAndTail() {
        String longBody = "START" + "x".repeat(2000) + "END";
        ToolResult r = tool(List.of(new SearchEntry("", "", longBody)))
                .execute(Map.of("query", "q"));
        assertThat(r.isSuccess()).isTrue();
        // No source → no "[source]" prefix; output starts directly with the body head
        assertThat(r.getOutput()).startsWith("START");
        assertThat(r.getOutput()).endsWith("END");
        assertThat(r.getOutput()).contains("chars omitted");
    }

    @Test
    void resultWithNoSource_noSourceBrackets() {
        ToolResult r = tool(List.of(new SearchEntry("", "Title", "body")))
                .execute(Map.of("query", "q"));
        assertThat(r.getOutput()).doesNotContain("[]");
        assertThat(r.getOutput()).contains("Title");
    }

    @Test
    void preCheck_returnsGuard_shortCircuits() {
        AbstractQueryTool guarded = new AbstractQueryTool() {
            @Override public String toolId() { return "GUARDED"; }
            @Override protected List<SearchEntry> search(String q) { return List.of(SearchEntry.bodyOnly("result")); }
            @Override protected ToolResult preCheck() { return ToolResult.failure("disabled"); }
        };
        ToolResult r = guarded.execute(Map.of("query", "anything"));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).isEqualTo("disabled");
    }
}
