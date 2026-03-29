package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.agent.plan.Step;
import fr.baretto.ollamassist.agent.tools.AgentTool;
import fr.baretto.ollamassist.agent.tools.ToolDispatcher;
import fr.baretto.ollamassist.agent.tools.ToolRegistry;
import fr.baretto.ollamassist.agent.tools.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ToolDispatcherTest {

    private ToolRegistry mockRegistry;
    private ToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        mockRegistry = mock(ToolRegistry.class);
        dispatcher = new ToolDispatcher(mockRegistry, null);
    }

    @Test
    void dispatch_unknownToolId_returnsFailure() {
        when(mockRegistry.get("UNKNOWN_TOOL")).thenReturn(null);
        Step step = new Step("UNKNOWN_TOOL", "do something", Map.of());

        ToolResult result = dispatcher.dispatch(step);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("UNKNOWN_TOOL");
    }

    @Test
    void dispatch_knownTool_returnsToolResult() {
        AgentTool mockTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_READ")).thenReturn(mockTool);
        when(mockTool.execute(any())).thenReturn(ToolResult.success("file content"));
        Step step = new Step("FILE_READ", "read main class", Map.of("path", "Main.java"));

        ToolResult result = dispatcher.dispatch(step);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).isEqualTo("file content");
        verify(mockTool).execute(Map.of("path", "Main.java"));
    }

    @Test
    void dispatch_toolThrowsException_returnsFailure() {
        AgentTool mockTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_EDIT")).thenReturn(mockTool);
        when(mockTool.execute(any())).thenThrow(new RuntimeException("disk error"));
        Step step = new Step("FILE_EDIT", "edit file", Map.of("path", "Foo.java"));

        ToolResult result = dispatcher.dispatch(step);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("FILE_EDIT").contains("disk error");
    }

    @Test
    void dispatch_passesStepParamsToTool() {
        AgentTool mockTool = mock(AgentTool.class);
        when(mockRegistry.get("CODE_SEARCH")).thenReturn(mockTool);
        when(mockTool.execute(any())).thenReturn(ToolResult.success("matches"));
        Map<String, Object> params = Map.of("query", "interface Foo");
        Step step = new Step("CODE_SEARCH", "search interface", params);

        dispatcher.dispatch(step);

        verify(mockTool).execute(params);
    }

    @Test
    void dispatch_withPreviousOutput_resolvesPlaceholder() {
        AgentTool mockTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_READ")).thenReturn(mockTool);
        when(mockTool.execute(any())).thenReturn(ToolResult.success("file content"));
        // Params contain a placeholder — the dispatcher should resolve it
        Step step = new Step("FILE_READ", "read found file", Map.of("path", "{{prev_output_first_line}}"));

        dispatcher.dispatch(step, "src/main/java/Foo.java\nsrc/test/java/FooTest.java");

        verify(mockTool).execute(Map.of("path", "src/main/java/Foo.java"));
    }

    @Test
    void dispatch_noPlaceholder_previousOutputIgnored() {
        AgentTool mockTool = mock(AgentTool.class);
        when(mockRegistry.get("FILE_READ")).thenReturn(mockTool);
        when(mockTool.execute(any())).thenReturn(ToolResult.success("content"));
        Map<String, Object> params = Map.of("path", "src/main/Foo.java");
        Step step = new Step("FILE_READ", "read file", params);

        dispatcher.dispatch(step, "some/other/path.java");

        verify(mockTool).execute(params);
    }
}
