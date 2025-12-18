package fr.baretto.ollamassist.mcp;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.docker.DockerMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import fr.baretto.ollamassist.setting.McpServerConfig;
import fr.baretto.ollamassist.setting.McpSettings;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service that manages MCP (Model Context Protocol) client lifecycle.
 * Creates and maintains MCP clients based on configuration.
 */
@Slf4j
@Service(Service.Level.PROJECT)
public final class McpClientManager implements Disposable {

    private final Project project;
    private final List<McpClient> activeClients = Collections.synchronizedList(new ArrayList<>());
    private volatile McpToolProvider toolProvider = null;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public McpClientManager(Project project) {
        this.project = project;
    }

    public static McpClientManager getInstance(Project project) {
        return project.getService(McpClientManager.class);
    }

    /**
     * Initialize or refresh MCP clients based on current settings.
     * Disposes existing clients and creates new ones.
     */
    public void initializeClients() {
        rwLock.writeLock().lock();
        try {
            log.info("Initializing MCP clients");

            // Dispose existing clients
            disposeClientsUnsafe();

            McpSettings settings = McpSettings.getInstance(project);

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

            // Filter: enabled in settings AND active in runtime
            McpRuntimeState runtimeState = McpRuntimeState.getInstance(project);
            List<McpServerConfig> enabledServers = serverConfigs.stream()
                    .filter(McpServerConfig::isEnabled)          // Configured
                    .filter(cfg -> runtimeState.isServerActive(cfg.getName()))  // Active
                    .toList();

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
                        throw new MCPException("MCP server '" + config.getName() + "' failed to initialize", e);
                    }
                    // Otherwise, continue with other servers (tolerance to failures)
                }
            }

            synchronized (activeClients) {
                activeClients.clear();
                activeClients.addAll(clients);
            }

            // Create tool provider if we have active clients
            if (!activeClients.isEmpty()) {
                createToolProvider();
            } else {
                log.warn("No MCP clients were successfully created");
            }
        } finally {
            rwLock.writeLock().unlock();
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

        var builder = StreamableHttpMcpTransport.builder()
                .url(config.getUrl())
                .logRequests(config.isLogRequests())
                .logResponses(config.isLogResponses());

        // Add authentication if configured
        if (config.getAuthToken() != null && !config.getAuthToken().isEmpty()) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + config.getAuthToken());
            builder.customHeaders(headers);
            log.debug("Added Bearer token authentication for server: {}", config.getName());
        }

        return builder.build();
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

        McpSettings settings = McpSettings.getInstance(project);
        McpRuntimeState runtimeState = McpRuntimeState.getInstance(project);
        List<McpServerConfig> enabledServers = settings.getMcpServers().stream()
                .filter(McpServerConfig::isEnabled)
                .filter(cfg -> runtimeState.isServerActive(cfg.getName()))
                .toList();

        McpToolProvider.Builder builder = McpToolProvider.builder()
                .mcpClients(activeClients.toArray(new McpClient[0]))
                .failIfOneServerFails(false); // Tolerance to failures

        // SINGLE mapper for ALL servers
        builder.toolNameMapper((client, toolSpec) ->
                enabledServers.stream()
                        .filter(cfg -> cfg.getName().equals(client.key()))
                        .filter(cfg -> cfg.getToolNamePrefix() != null && !cfg.getToolNamePrefix().isEmpty())
                        .findFirst()
                        .map(cfg -> cfg.getToolNamePrefix() + "_" + toolSpec.name())
                        .orElse(toolSpec.name())
        );

        // Collect ALL allowed tools from ALL servers
        Set<String> allAllowedTools = new HashSet<>();
        for (McpServerConfig config : enabledServers) {
            if (config.getFilterToolNames() != null && !config.getFilterToolNames().isEmpty()) {
                Arrays.stream(config.getFilterToolNames().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(allAllowedTools::add);
            }
        }

        if (!allAllowedTools.isEmpty()) {
            builder.filterToolNames(allAllowedTools.toArray(new String[0]));
        }

        // Add approval wrapper if approval is required
        if (settings.isMcpApprovalRequired()) {
            log.info("MCP approval is required, adding approval wrapper");

            // Create a map of tool name prefix -> server name for approval tracking
            Map<String, String> prefixToServerName = new HashMap<>();
            for (McpServerConfig config : enabledServers) {
                if (config.getToolNamePrefix() != null && !config.getToolNamePrefix().isEmpty()) {
                    prefixToServerName.put(config.getToolNamePrefix(), config.getName());
                } else {
                    // If no prefix, we'll use the server name as fallback
                    prefixToServerName.put("", config.getName());
                }
            }

            McpApprovalService approvalService = McpApprovalService.getInstance(project);

            builder.toolWrapper(delegate ->
                    // Try to determine server name from tool execution request
                    // This will be resolved at execution time
                    new ApprovalToolExecutor(
                            delegate,
                            approvalService,
                            project,
                            prefixToServerName,
                            enabledServers
                    )
            );
        }

        toolProvider = builder.build();
        log.info("McpToolProvider created successfully");
    }

    /**
     * Get the tool provider, or null if no MCP clients are active.
     */
    public McpToolProvider getToolProvider() {
        rwLock.readLock().lock();
        try {
            return toolProvider;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Check if MCP is enabled and has active clients.
     */
    public boolean isEnabled() {
        rwLock.readLock().lock();
        try {
            return McpSettings.getInstance(project).isMcpEnabled() && !activeClients.isEmpty();
        } finally {
            rwLock.readLock().unlock();
        }
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
     * Dispose all active clients (thread-safe version).
     */
    private void disposeClients() {
        rwLock.writeLock().lock();
        try {
            disposeClientsUnsafe();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Dispose all active clients (must be called within write lock).
     */
    private void disposeClientsUnsafe() {
        if (!activeClients.isEmpty()) {
            log.info("Disposing {} MCP clients", activeClients.size());
            // Create a copy to avoid ConcurrentModificationException
            List<McpClient> clientsCopy;
            synchronized (activeClients) {
                clientsCopy = new ArrayList<>(activeClients);
            }
            clientsCopy.forEach(this::disposeClient);
            synchronized (activeClients) {
                activeClients.clear();
            }
        }
        toolProvider = null;
    }

    @Override
    public void dispose() {
        log.info("McpClientManager disposing");
        disposeClients();
    }


    private static class MCPException extends RuntimeException {

        public MCPException(String s, Exception e) {
            super(s, e);
        }
    }
}
