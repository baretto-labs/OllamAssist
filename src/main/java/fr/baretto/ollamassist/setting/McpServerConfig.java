package fr.baretto.ollamassist.setting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a Model Context Protocol (MCP) server.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpServerConfig {

    /**
     * Unique name for the MCP server
     */
    private String name = "";

    /**
     * Type of MCP transport
     */
    private TransportType type = TransportType.STDIO;

    /**
     * Whether the server is enabled
     */
    private boolean enabled = true;

    /**
     * Command to execute for STDIO transport (e.g., ["npm", "exec", "server"])
     */
    private List<String> command = new ArrayList<>();

    /**
     * URL for HTTP/SSE transport (e.g., "http://localhost:3001/mcp")
     */
    private String url = "";

    /**
     * Docker image name for Docker transport (e.g., "mcp/time")
     */
    private String dockerImage = "";

    /**
     * Docker host URL (e.g., "unix:///var/run/docker.sock")
     */
    private String dockerHost = "unix:///var/run/docker.sock";

    // Advanced configuration options

    /**
     * Enable logging of MCP events
     */
    private boolean logEvents = false;

    /**
     * Enable logging of HTTP requests (for HTTP/SSE transport)
     */
    private boolean logRequests = false;

    /**
     * Enable logging of HTTP responses (for HTTP/SSE transport)
     */
    private boolean logResponses = false;

    /**
     * Filter tools by name (comma-separated list). Empty means all tools are available.
     */
    private String filterToolNames = "";

    /**
     * Prefix to add to tool names to avoid conflicts
     */
    private String toolNamePrefix = "";

    /**
     * Fail the entire assistant initialization if this server fails to connect
     */
    private boolean failOnError = false;

    /**
     * Environment variables to pass to the MCP server (e.g., API keys, tokens).
     * Format: KEY=value, one per line or comma-separated
     */
    private String environmentVariables = "";

    /**
     * Authentication token for HTTP/SSE transport (e.g., Bearer token for API authentication).
     * This will be sent as "Authorization: Bearer <token>" header.
     */
    private String authToken = "";

    @Getter
    public enum TransportType {
        STDIO("Stdio (Local Process)"),
        HTTP_SSE("HTTP/SSE (Server)"),
        DOCKER("Docker Container");

        private final String displayName;

        TransportType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Get the transport-specific configuration as a string for display
     */
    public String getConfigurationSummary() {
        return switch (type) {
            case STDIO -> String.join(" ", command);
            case HTTP_SSE -> url;
            case DOCKER -> dockerImage;
        };
    }
}
