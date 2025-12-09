package fr.baretto.ollamassist.mcp;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.docker.DockerMcpTransport;
import fr.baretto.ollamassist.setting.McpServerConfig;
import fr.baretto.ollamassist.setting.McpSettings;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that manages MCP (Model Context Protocol) client lifecycle.
 * Creates and maintains MCP clients based on configuration.
 */
@Slf4j
@Service
public final class McpClientManager implements Disposable {

    private List<McpClient> activeClients = new ArrayList<>();
    private McpToolProvider toolProvider = null;

    public static McpClientManager getInstance() {
        return ApplicationManager.getApplication().getService(McpClientManager.class);
    }

    /**
     * Initialize or refresh MCP clients based on current settings.
     * Disposes existing clients and creates new ones.
     */
    public synchronized void initializeClients() {
        log.info("Initializing MCP clients");

        // Dispose existing clients
        disposeClients();

        McpSettings settings = McpSettings.getInstance();

        // If MCP is disabled, just return
        if (!settings.isMcpEnabled()) {
            log.info("MCP integration is disabled");
            return;
        }

        List<McpServerConfig> serverConfigs = settings.getMcpServers();
        if (serverConfigs == null || serverConfigs.isEmpty()) {
            log.info("No MCP servers configured");
            return;
        }

        // Filter only enabled servers
        List<McpServerConfig> enabledServers = serverConfigs.stream()
                .filter(McpServerConfig::isEnabled)
                .collect(Collectors.toList());

        if (enabledServers.isEmpty()) {
            log.info("No enabled MCP servers found");
            return;
        }

        log.info("Creating {} MCP clients", enabledServers.size());

        // Create clients for each enabled server
        List<McpClient> clients = new ArrayList<>();
        for (McpServerConfig config : enabledServers) {
            try {
                McpClient client = createClient(config);
                if (client != null) {
                    clients.add(client);
                    log.info("Successfully created MCP client for server: {}", config.getName());
                }
            } catch (Exception e) {
                log.error("Failed to create MCP client for server: {}", config.getName(), e);
                if (config.isFailOnError()) {
                    // If this server is configured to fail on error, dispose all clients and throw
                    clients.forEach(this::disposeClient);
                    throw new RuntimeException("MCP server '" + config.getName() + "' failed to initialize", e);
                }
                // Otherwise, continue with other servers (tolerance to failures)
            }
        }

        activeClients = clients;

        // Create tool provider if we have active clients
        if (!activeClients.isEmpty()) {
            createToolProvider();
        } else {
            log.warn("No MCP clients were successfully created");
        }
    }

    /**
     * Create an MCP client from configuration.
     */
    private McpClient createClient(McpServerConfig config) {
        log.debug("Creating MCP client for server: {} (type: {})", config.getName(), config.getType());

        McpTransport transport = createTransport(config);
        if (transport == null) {
            log.error("Failed to create transport for server: {}", config.getName());
            return null;
        }

        return DefaultMcpClient.builder()
                .key(config.getName())
                .transport(transport)
                .build();
    }

    /**
     * Create the appropriate transport based on configuration.
     */
    private McpTransport createTransport(McpServerConfig config) {
        return switch (config.getType()) {
            case STDIO -> createStdioTransport(config);
            case HTTP_SSE -> createHttpTransport(config);
            case DOCKER -> createDockerTransport(config);
        };
    }

    /**
     * Create stdio transport for local process execution.
     */
    private McpTransport createStdioTransport(McpServerConfig config) {
        if (config.getCommand() == null || config.getCommand().isEmpty()) {
            log.error("No command specified for stdio transport: {}", config.getName());
            return null;
        }

        log.debug("Creating stdio transport with command: {}", config.getCommand());

        var builder = StdioMcpTransport.builder()
                .command(config.getCommand())
                .logEvents(config.isLogEvents());

        // Add environment variables if configured
        Map<String, String> envVars = parseEnvironmentVariables(config.getEnvironmentVariables());
        if (!envVars.isEmpty()) {
            log.debug("Adding {} environment variables for server: {}", envVars.size(), config.getName());
            builder.environment(envVars);
        }

        return builder.build();
    }

    /**
     * Parse environment variables from string format (KEY=value, one per line).
     */
    private Map<String, String> parseEnvironmentVariables(String envVarsString) {
        Map<String, String> envVars = new HashMap<>();
        if (envVarsString == null || envVarsString.trim().isEmpty()) {
            return envVars;
        }

        String[] lines = envVarsString.split("[\n\r]+");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue; // Skip empty lines and comments
            }

            int equalsIndex = line.indexOf('=');
            if (equalsIndex > 0 && equalsIndex < line.length() - 1) {
                String key = line.substring(0, equalsIndex).trim();
                String value = line.substring(equalsIndex + 1).trim();
                if (!key.isEmpty()) {
                    envVars.put(key, value);
                    log.debug("Parsed environment variable: {}=***", key); // Don't log the value for security
                }
            } else {
                log.warn("Invalid environment variable format (expected KEY=value): {}", line);
            }
        }

        return envVars;
    }

    /**
     * Create HTTP/SSE transport for remote servers.
     */
    private McpTransport createHttpTransport(McpServerConfig config) {
        if (config.getUrl() == null || config.getUrl().isEmpty()) {
            log.error("No URL specified for HTTP/SSE transport: {}", config.getName());
            return null;
        }

        log.debug("Creating HTTP/SSE transport with URL: {}", config.getUrl());

        return StreamableHttpMcpTransport.builder()
                .url(config.getUrl())
                .logRequests(config.isLogRequests())
                .logResponses(config.isLogResponses())
                .build();
    }

    /**
     * Create Docker transport for containerized servers.
     */
    private McpTransport createDockerTransport(McpServerConfig config) {
        if (config.getDockerImage() == null || config.getDockerImage().isEmpty()) {
            log.error("No Docker image specified for Docker transport: {}", config.getName());
            return null;
        }

        log.debug("Creating Docker transport with image: {} and host: {}",
                config.getDockerImage(), config.getDockerHost());

        return DockerMcpTransport.builder()
                .image(config.getDockerImage())
                .dockerHost(config.getDockerHost())
                .build();
    }

    /**
     * Create the tool provider from active clients.
     */
    private void createToolProvider() {
        log.info("Creating McpToolProvider with {} clients", activeClients.size());

        McpToolProvider.Builder builder = McpToolProvider.builder()
                .mcpClients(activeClients.toArray(new McpClient[0]))
                .failIfOneServerFails(false); // Tolerance to failures

        // Apply advanced configuration from each server
        McpSettings settings = McpSettings.getInstance();
        List<McpServerConfig> enabledServers = settings.getMcpServers().stream()
                .filter(McpServerConfig::isEnabled)
                .collect(Collectors.toList());

        for (McpServerConfig config : enabledServers) {
            // Apply tool name prefix if configured
            if (config.getToolNamePrefix() != null && !config.getToolNamePrefix().isEmpty()) {
                String prefix = config.getToolNamePrefix();
                builder.toolNameMapper((client, toolSpec) -> {
                    if (client.key().equals(config.getName())) {
                        return prefix + "_" + toolSpec.name();
                    }
                    return toolSpec.name();
                });
            }

            // Apply tool name filtering if configured
            if (config.getFilterToolNames() != null && !config.getFilterToolNames().isEmpty()) {
                List<String> allowedTools = Arrays.stream(config.getFilterToolNames().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

                if (!allowedTools.isEmpty()) {
                    builder.filterToolNames(allowedTools.toArray(new String[0]));
                }
            }
        }

        toolProvider = builder.build();
        log.info("McpToolProvider created successfully");
    }

    /**
     * Get the tool provider, or null if no MCP clients are active.
     */
    public synchronized McpToolProvider getToolProvider() {
        return toolProvider;
    }

    /**
     * Check if MCP is enabled and has active clients.
     */
    public synchronized boolean isEnabled() {
        return McpSettings.getInstance().isMcpEnabled() && !activeClients.isEmpty();
    }

    /**
     * Dispose a single client.
     */
    private void disposeClient(McpClient client) {
        if (client != null) {
            try {
                log.debug("Disposing MCP client: {}", client.key());
                client.close();
            } catch (Exception e) {
                log.error("Error disposing MCP client: {}", client.key(), e);
            }
        }
    }

    /**
     * Dispose all active clients.
     */
    private synchronized void disposeClients() {
        if (!activeClients.isEmpty()) {
            log.info("Disposing {} MCP clients", activeClients.size());
            activeClients.forEach(this::disposeClient);
            activeClients.clear();
        }
        toolProvider = null;
    }

    @Override
    public void dispose() {
        log.info("McpClientManager disposing");
        disposeClients();
    }
}
