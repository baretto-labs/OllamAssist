package fr.baretto.ollamassist.core.mcp.connection;

import fr.baretto.ollamassist.core.mcp.protocol.MCPMessage;
import fr.baretto.ollamassist.core.mcp.protocol.MCPResponse;
import fr.baretto.ollamassist.core.mcp.server.MCPServerConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connexion vers un serveur MCP intégré (built-in)
 * Ces serveurs sont implémentés directement dans le plugin
 */
@Slf4j
public class BuiltinMCPConnection implements MCPConnection {

    private final MCPServerConfig serverConfig;
    private final AtomicInteger messagesSent = new AtomicInteger(0);
    private final AtomicInteger messagesReceived = new AtomicInteger(0);

    private boolean connected = false;
    private long connectedAt;
    private long lastActivityAt;
    private String lastError;

    public BuiltinMCPConnection(MCPServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    @Override
    public void connect() throws MCPConnectionException {
        try {
            log.info("Connecting to builtin MCP server: {}", serverConfig.getName());

            // Pour les serveurs intégrés, la "connexion" est immédiate
            connected = true;
            connectedAt = System.currentTimeMillis();
            lastActivityAt = connectedAt;

            log.info("Successfully connected to builtin MCP server: {}", serverConfig.getName());

        } catch (Exception e) {
            lastError = e.getMessage();
            throw new MCPConnectionException("Failed to connect to builtin server", e);
        }
    }

    @Override
    public void disconnect() throws MCPConnectionException {
        log.info("Disconnecting from builtin MCP server: {}", serverConfig.getName());
        connected = false;
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

                // Traitement du message pour les serveurs intégrés
                MCPResponse response = processBuiltinMessage(message);

                messagesReceived.incrementAndGet();
                return response;

            } catch (Exception e) {
                lastError = e.getMessage();
                log.error("Error processing message for builtin server", e);
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
                .connectionType("builtin")
                .connectedAt(connectedAt)
                .lastActivityAt(lastActivityAt)
                .messagesSent(messagesSent.get())
                .messagesReceived(messagesReceived.get())
                .isHealthy(isHealthy())
                .lastError(lastError)
                .build();
    }

    private MCPResponse processBuiltinMessage(MCPMessage message) {
        // Implémentation basique pour les serveurs intégrés
        log.debug("Processing builtin message: {}", message.getMethod());

        return switch (message.getMethod()) {
            case "filesystem/read" -> processFileSystemRead(message);
            case "filesystem/write" -> processFileSystemWrite(message);
            case "filesystem/list" -> processFileSystemList(message);
            default -> MCPResponse.error("Unknown method: " + message.getMethod());
        };
    }

    private MCPResponse processFileSystemRead(MCPMessage message) {
        // TODO: Implémenter la lecture de fichiers
        return MCPResponse.success("File read operation (not implemented yet)");
    }

    private MCPResponse processFileSystemWrite(MCPMessage message) {
        // TODO: Implémenter l'écriture de fichiers
        return MCPResponse.success("File write operation (not implemented yet)");
    }

    private MCPResponse processFileSystemList(MCPMessage message) {
        // TODO: Implémenter la liste de fichiers
        return MCPResponse.success("File list operation (not implemented yet)");
    }
}