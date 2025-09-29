package fr.baretto.ollamassist.core.mcp.connection;

import fr.baretto.ollamassist.core.mcp.protocol.MCPMessage;
import fr.baretto.ollamassist.core.mcp.protocol.MCPResponse;
import fr.baretto.ollamassist.core.mcp.server.MCPServerConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connexion WebSocket vers un serveur MCP externe
 */
@Slf4j
public class WebSocketMCPConnection implements MCPConnection {

    private final MCPServerConfig serverConfig;
    private final AtomicInteger messagesSent = new AtomicInteger(0);
    private final AtomicInteger messagesReceived = new AtomicInteger(0);

    private boolean connected = false;
    private long connectedAt;
    private long lastActivityAt;
    private String lastError;

    // TODO: Ajouter le client WebSocket
    // private WebSocket webSocket;

    public WebSocketMCPConnection(MCPServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    @Override
    public void connect() throws MCPConnectionException {
        try {
            log.info("Connecting to WebSocket MCP server: {} at {}",
                    serverConfig.getName(), serverConfig.getEndpoint());

            // TODO: Initialiser le client WebSocket
            // OkHttpClient client = new OkHttpClient();
            // Request request = new Request.Builder()
            //     .url(serverConfig.getEndpoint())
            //     .build();
            //
            // webSocket = client.newWebSocket(request, new WebSocketListener() {
            //     @Override
            //     public void onOpen(WebSocket webSocket, Response response) {
            //         connected = true;
            //         connectedAt = System.currentTimeMillis();
            //         lastActivityAt = connectedAt;
            //     }
            //
            //     @Override
            //     public void onMessage(WebSocket webSocket, String text) {
            //         messagesReceived.incrementAndGet();
            //         lastActivityAt = System.currentTimeMillis();
            //         // Traiter le message reçu
            //     }
            //
            //     @Override
            //     public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            //         lastError = t.getMessage();
            //         connected = false;
            //     }
            // });

            // Simulation de connexion pour l'instant
            connected = true;
            connectedAt = System.currentTimeMillis();
            lastActivityAt = connectedAt;

            log.info("Successfully connected to WebSocket MCP server: {}", serverConfig.getName());

        } catch (Exception e) {
            lastError = e.getMessage();
            throw new MCPConnectionException("Failed to connect to WebSocket server", e);
        }
    }

    @Override
    public void disconnect() throws MCPConnectionException {
        log.info("Disconnecting from WebSocket MCP server: {}", serverConfig.getName());
        connected = false;

        // TODO: Fermer la connexion WebSocket
        // if (webSocket != null) {
        //     webSocket.close(1000, "Normal closure");
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

                // TODO: Implémenter l'envoi WebSocket réel
                // webSocket.send(message.toJson());

                // Simulation pour l'instant
                MCPResponse response = MCPResponse.success("WebSocket message processed (simulation)");

                messagesReceived.incrementAndGet();
                return response;

            } catch (Exception e) {
                lastError = e.getMessage();
                log.error("Error sending WebSocket message", e);
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
                .connectionType("websocket")
                .connectedAt(connectedAt)
                .lastActivityAt(lastActivityAt)
                .messagesSent(messagesSent.get())
                .messagesReceived(messagesReceived.get())
                .isHealthy(isHealthy())
                .lastError(lastError)
                .build();
    }
}