package fr.baretto.ollamassist.core.mcp.connection;

import fr.baretto.ollamassist.core.mcp.protocol.MCPMessage;
import fr.baretto.ollamassist.core.mcp.protocol.MCPResponse;
import fr.baretto.ollamassist.core.mcp.server.MCPServerConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connexion HTTP vers un serveur MCP externe
 */
@Slf4j
public class HttpMCPConnection implements MCPConnection {

    private final MCPServerConfig serverConfig;
    private final AtomicInteger messagesSent = new AtomicInteger(0);
    private final AtomicInteger messagesReceived = new AtomicInteger(0);

    private boolean connected = false;
    private long connectedAt;
    private long lastActivityAt;
    private String lastError;

    // TODO: Ajouter le client HTTP (OkHttp)
    // private OkHttpClient httpClient;

    public HttpMCPConnection(MCPServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    @Override
    public void connect() throws MCPConnectionException {
        try {
            log.info("Connecting to HTTP MCP server: {} at {}",
                    serverConfig.getName(), serverConfig.getEndpoint());

            // TODO: Initialiser le client HTTP et tester la connexion
            // httpClient = new OkHttpClient.Builder()
            //     .connectTimeout(serverConfig.getTimeout(), TimeUnit.MILLISECONDS)
            //     .build();

            // Simulation de connexion pour l'instant
            connected = true;
            connectedAt = System.currentTimeMillis();
            lastActivityAt = connectedAt;

            log.info("Successfully connected to HTTP MCP server: {}", serverConfig.getName());

        } catch (Exception e) {
            lastError = e.getMessage();
            throw new MCPConnectionException("Failed to connect to HTTP server", e);
        }
    }

    @Override
    public void disconnect() throws MCPConnectionException {
        log.info("Disconnecting from HTTP MCP server: {}", serverConfig.getName());
        connected = false;

        // TODO: Fermer le client HTTP
        // if (httpClient != null) {
        //     httpClient.dispatcher().executorService().shutdown();
        //     httpClient.connectionPool().evictAll();
        // }
    }

    @Override
    public CompletableFuture<MCPResponse> sendMessage(MCPMessage message) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                throw new RuntimeException("Not connected to server");
            }

            try {
                messagesSent.incrementAndGet();
                lastActivityAt = System.currentTimeMillis();

                // TODO: Implémenter l'envoi HTTP réel
                // Request request = new Request.Builder()
                //     .url(serverConfig.getEndpoint())
                //     .post(RequestBody.create(message.toJson(), MediaType.get("application/json")))
                //     .build();
                //
                // Response response = httpClient.newCall(request).execute();
                // MCPResponse mcpResponse = MCPResponse.fromJson(response.body().string());

                // Simulation pour l'instant
                MCPResponse response = MCPResponse.success("HTTP message processed (simulation)");

                messagesReceived.incrementAndGet();
                return response;

            } catch (Exception e) {
                lastError = e.getMessage();
                log.error("Error sending HTTP message", e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean isHealthy() {
        return connected && lastError == null;
    }

    @Override
    public MCPServerConfig getServerConfig() {
        return serverConfig;
    }

    @Override
    public MCPConnectionInfo getConnectionInfo() {
        return MCPConnectionInfo.builder()
                .serverId(serverConfig.getId())
                .serverName(serverConfig.getName())
                .connectionType("http")
                .connectedAt(connectedAt)
                .lastActivityAt(lastActivityAt)
                .messagesSent(messagesSent.get())
                .messagesReceived(messagesReceived.get())
                .isHealthy(isHealthy())
                .lastError(lastError)
                .build();
    }
}