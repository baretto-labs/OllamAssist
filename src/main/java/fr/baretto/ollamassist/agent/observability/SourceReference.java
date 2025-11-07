package fr.baretto.ollamassist.agent.observability;

import lombok.Builder;
import lombok.Data;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;

/**
 * References to source code, files, or documentation used during agent execution.
 * Provides navigation capabilities for IntelliJ integration.
 */
@Data
@Builder
public class SourceReference {
    /** File path, URL, or identifier */
    private String uri;

    /** Type of source */
    private SourceType type;

    /** Start line (1-based) */
    private Integer lineStart;

    /** End line (inclusive) */
    private Integer lineEnd;

    /** Start column (optional) */
    private Integer columnStart;

    /** End column (optional) */
    private Integer columnEnd;

    /** Code/text excerpt (max 500 chars) */
    private String snippet;

    /** Human-readable description */
    private String description;

    /** Confidence/relevance (0.0-1.0) */
    private Double relevanceScore;

    /** Agent that produced this source */
    private String sourceAgent;

    /** When source was created */
    private Instant timestamp;

    /** Additional metadata */
    private Map<String, String> metadata;

    /**
     * Returns display name for UI (filename for files, URI otherwise).
     */
    public String getDisplayName() {
        if (type == SourceType.FILE && uri != null) {
            return Paths.get(uri).getFileName().toString();
        }
        return uri;
    }

    /**
     * Returns navigation URL for IntelliJ.
     */
    public String getNavigationUrl() {
        if (type == SourceType.FILE && uri != null && lineStart != null) {
            return String.format("file://%s:%d", uri, lineStart);
        }
        return uri;
    }

    /**
     * Returns true if this source can be navigated to in IntelliJ.
     */
    public boolean isNavigable() {
        return type == SourceType.FILE ||
               type == SourceType.CLASS ||
               type == SourceType.COMMIT;
    }
}
