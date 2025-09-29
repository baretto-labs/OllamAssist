package fr.baretto.ollamassist.core.mcp.connection;

import fr.baretto.ollamassist.core.mcp.protocol.MCPMessage;
import fr.baretto.ollamassist.core.mcp.protocol.MCPResponse;
import fr.baretto.ollamassist.core.mcp.server.MCPServerConfig;

import java.util.concurrent.CompletableFuture;

/**
 * Interface représentant une connexion vers un serveur MCP
 */
public interface MCPConnection {

    /**
     * Établit la connexion vers le serveur MCP
     */
    void connect() throws MCPConnectionException;

    /**
     * Ferme la connexion vers le serveur MCP
     */
    void disconnect() throws MCPConnectionException;

    /**
     * Envoie un message au serveur MCP
     */
    CompletableFuture<MCPResponse> sendMessage(MCPMessage message);

    /**
     * Vérifie si la connexion est active
     */
    boolean isConnected();

    /**
     * Vérifie si la connexion est en bonne santé
     */
    boolean isHealthy();

    /**
     * Obtient la configuration du serveur
     */
    MCPServerConfig getServerConfig();

    /**
     * Obtient les statistiques de la connexion
     */
    MCPConnectionInfo getConnectionInfo();

    /**
     * Exception spécifique aux connexions MCP
     */
    class MCPConnectionException extends Exception {
        public MCPConnectionException(String message) {
            super(message);
        }

        public MCPConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Informations sur une connexion MCP
     */
    @lombok.Builder
    @lombok.Value
    class MCPConnectionInfo {
        String serverId;
        String serverName;
        String connectionType;
        long connectedAt;
        long lastActivityAt;
        int messagesSent;
        int messagesReceived;
        boolean isHealthy;
        String lastError;
    }
}