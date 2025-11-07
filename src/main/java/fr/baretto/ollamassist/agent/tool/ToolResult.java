package fr.baretto.ollamassist.agent.tool;

import fr.baretto.ollamassist.agent.observability.SourceReference;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of a tool execution.
 * Contains output, sources, and metadata for observability.
 */
@Data
@Builder
public class ToolResult {
    private String output;
    private Object structuredData;
    private List<SourceReference> sources;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    private boolean success;
    private String errorMessage;

    /**
     * Creates a successful tool result.
     */
    public static ToolResult success(String output, List<SourceReference> sources) {
        return ToolResult.builder()
            .output(output)
            .sources(sources)
            .success(true)
            .build();
    }

    /**
     * Creates a failed tool result.
     */
    public static ToolResult error(String errorMessage) {
        return ToolResult.builder()
            .errorMessage(errorMessage)
            .success(false)
            .build();
    }

    /**
     * Adds structured data to the result.
     */
    public ToolResult withStructuredData(Object data) {
        this.structuredData = data;
        return this;
    }

    /**
     * Adds metadata to the result.
     */
    public ToolResult withMetadata(Map<String, Object> metadata) {
        this.metadata.putAll(metadata);
        return this;
    }
}
