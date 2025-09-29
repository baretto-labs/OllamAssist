package fr.baretto.ollamassist.core.mcp.connection;

import fr.baretto.ollamassist.core.mcp.server.MCPServerConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory pour créer des connexions MCP selon le type de serveur
 */
@Slf4j
public class MCPConnectionFactory {

    /**
     * Crée une connexion MCP selon la configuration du serveur
     */
    public MCPConnection createConnection(MCPServerConfig serverConfig) {
        log.debug("Creating connection for server type: {}", serverConfig.getType());

        return switch (serverConfig.getType()) {
            case BUILTIN -> new BuiltinMCPConnection(serverConfig);
            case HTTP -> new HttpMCPConnection(serverConfig);
            case WEBSOCKET -> new WebSocketMCPConnection(serverConfig);
            default -> throw new IllegalArgumentException("Unsupported server type: " + serverConfig.getType());
        };
    }
}