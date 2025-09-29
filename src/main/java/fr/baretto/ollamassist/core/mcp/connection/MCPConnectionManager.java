package fr.baretto.ollamassist.core.mcp.connection;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.mcp.protocol.MCPMessage;
import fr.baretto.ollamassist.core.mcp.protocol.MCPResponse;
import fr.baretto.ollamassist.core.mcp.server.MCPServerConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Gestionnaire de connexions MCP
 * Responsable de l'établissement, la maintenance et la fermeture des connexions aux serveurs MCP
 */
@Slf4j
@Service(Service.Level.PROJECT)
public final class MCPConnectionManager {

    private final Project project;
    private final ConcurrentMap<String, MCPConnection> activeConnections = new ConcurrentHashMap<>();
    private final MCPConnectionFactory connectionFactory;

    public MCPConnectionManager(Project project) {
        this.project = project;
        this.connectionFactory = new MCPConnectionFactory();
        log.info("MCPConnectionManager initialized for project: {}", project.getName());
    }

    /**
     * Établit une connexion vers un serveur MCP
     */
    public CompletableFuture<MCPConnection> connect(MCPServerConfig serverConfig) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Connecting to MCP server: {}", serverConfig.getName());

                // Vérifier si une connexion existe déjà
                String serverId = serverConfig.getId();
                MCPConnection existingConnection = activeConnections.get(serverId);
                if (existingConnection != null && existingConnection.isConnected()) {
                    log.debug("Using existing connection to server: {}", serverId);
                    return existingConnection;
                }

                // Créer une nouvelle connexion
                MCPConnection connection = connectionFactory.createConnection(serverConfig);

                // Établir la connexion
                connection.connect();

                // Stocker la connexion
                activeConnections.put(serverId, connection);

                log.info("Successfully connected to MCP server: {}", serverConfig.getName());
                return connection;

            } catch (Exception e) {
                log.error("Failed to connect to MCP server: {}", serverConfig.getName(), e);
                throw new MCPConnectionException("Failed to connect to server: " + serverConfig.getName(), e);
            }
        });
    }

    /**
     * Ferme une connexion vers un serveur MCP
     */
    public CompletableFuture<Void> disconnect(String serverId) {
        return CompletableFuture.runAsync(() -> {
            MCPConnection connection = activeConnections.remove(serverId);
            if (connection != null) {
                try {
                    connection.disconnect();
                    log.info("Disconnected from MCP server: {}", serverId);
                } catch (Exception e) {
                    log.warn("Error while disconnecting from server: {}", serverId, e);
                }
            }
        });
    }

    /**
     * Envoie un message à un serveur MCP
     */
    public CompletableFuture<MCPResponse> sendMessage(String serverId, MCPMessage message) {
        MCPConnection connection = activeConnections.get(serverId);
        if (connection == null || !connection.isConnected()) {
            return CompletableFuture.failedFuture(
                    new MCPConnectionException("No active connection to server: " + serverId)
            );
        }

        return connection.sendMessage(message);
    }

    /**
     * Obtient la liste des connexions actives
     */
    public List<MCPConnection> getActiveConnections() {
        return new ArrayList<>(activeConnections.values());
    }

    /**
     * Vérifie si une connexion est active pour un serveur
     */
    public boolean isConnected(String serverId) {
        MCPConnection connection = activeConnections.get(serverId);
        return connection != null && connection.isConnected();
    }

    /**
     * Obtient les statistiques de connexion
     */
    public MCPConnectionStats getConnectionStats() {
        int totalConnections = activeConnections.size();
        long healthyConnections = activeConnections.values().stream()
                .mapToLong(conn -> conn.isHealthy() ? 1 : 0)
                .sum();

        return MCPConnectionStats.builder()
                .totalConnections(totalConnections)
                .healthyConnections((int) healthyConnections)
                .unhealthyConnections(totalConnections - (int) healthyConnections)
                .build();
    }

    /**
     * Ferme toutes les connexions actives
     */
    public CompletableFuture<Void> disconnectAll() {
        return CompletableFuture.runAsync(() -> {
            log.info("Closing all MCP connections. Count: {}", activeConnections.size());

            List<CompletableFuture<Void>> disconnectTasks = activeConnections.keySet().stream()
                    .map(this::disconnect)
                    .toList();

            CompletableFuture.allOf(disconnectTasks.toArray(new CompletableFuture[0])).join();

            activeConnections.clear();
            log.info("All MCP connections closed");
        });
    }

    /**
     * Vérifie la santé de toutes les connexions
     */
    public CompletableFuture<Void> healthCheck() {
        return CompletableFuture.runAsync(() -> {
            log.debug("Performing health check on {} connections", activeConnections.size());

            activeConnections.entrySet().removeIf(entry -> {
                MCPConnection connection = entry.getValue();
                if (!connection.isHealthy()) {
                    log.warn("Removing unhealthy connection: {}", entry.getKey());
                    try {
                        connection.disconnect();
                    } catch (Exception e) {
                        log.warn("Error closing unhealthy connection", e);
                    }
                    return true;
                }
                return false;
            });
        });
    }

    /**
     * Exception spécifique aux connexions MCP
     */
    public static class MCPConnectionException extends RuntimeException {
        public MCPConnectionException(String message) {
            super(message);
        }

        public MCPConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Statistiques de connexion MCP
     */
    @lombok.Builder
    @lombok.Value
    public static class MCPConnectionStats {
        int totalConnections;
        int healthyConnections;
        int unhealthyConnections;

        public double getHealthRatio() {
            return totalConnections > 0 ? (double) healthyConnections / totalConnections : 0.0;
        }
    }
}