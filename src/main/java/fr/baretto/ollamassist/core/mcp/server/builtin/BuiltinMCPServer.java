package fr.baretto.ollamassist.core.mcp.server.builtin;

import fr.baretto.ollamassist.core.mcp.protocol.MCPResponse;
import fr.baretto.ollamassist.core.mcp.server.MCPServerConfig;

import java.util.Map;

/**
 * Interface pour les serveurs MCP intégrés
 */
public interface BuiltinMCPServer {

    /**
     * Identifiant unique du serveur
     */
    String getId();

    /**
     * Nom du serveur
     */
    String getName();

    /**
     * Version du serveur
     */
    String getVersion();

    /**
     * Configuration du serveur
     */
    MCPServerConfig getConfig();

    /**
     * Exécute une capacité avec les paramètres donnés
     */
    MCPResponse executeCapability(String capability, Map<String, Object> params);

    /**
     * Indique si le serveur est disponible
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Démarre le serveur (pour les serveurs qui nécessitent une initialisation)
     */
    default void start() {
        // Par défaut, rien à faire
    }

    /**
     * Arrête le serveur
     */
    default void stop() {
        // Par défaut, rien à faire
    }
}