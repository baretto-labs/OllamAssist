package fr.baretto.ollamassist.core.mcp.server.builtin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import fr.baretto.ollamassist.core.mcp.protocol.MCPResponse;
import fr.baretto.ollamassist.core.mcp.server.MCPServerConfig;
import fr.baretto.ollamassist.core.mcp.server.MCPServerRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gestionnaire pour les serveurs MCP intégrés
 */
@Slf4j
@Service
public final class BuiltinServerManager {

    private final Map<String, BuiltinMCPServer> builtinServers = new HashMap<>();
    private final MCPServerRegistry serverRegistry;

    public BuiltinServerManager() {
        this.serverRegistry = ApplicationManager.getApplication().getService(MCPServerRegistry.class);
        initializeBuiltinServers();
    }

    private void initializeBuiltinServers() {
        log.info("Initializing builtin MCP servers");

        // Enregistrer les serveurs intégrés
        registerBuiltinServer(new FileSystemMCPServer());
        registerBuiltinServer(new WebSearchMCPServer());

        log.info("Initialized {} builtin MCP servers", builtinServers.size());
    }

    private void registerBuiltinServer(BuiltinMCPServer server) {
        try {
            server.start();
            builtinServers.put(server.getId(), server);

            // Enregistrer dans le registry global
            serverRegistry.registerServer(server.getConfig());

            log.debug("Registered builtin server: {} ({})", server.getName(), server.getId());
        } catch (Exception e) {
            log.error("Failed to register builtin server: {}", server.getId(), e);
        }
    }

    /**
     * Exécute une capacité sur un serveur intégré
     */
    public MCPResponse executeCapability(String serverId, String capability, Map<String, Object> params) {
        BuiltinMCPServer server = builtinServers.get(serverId);
        if (server == null) {
            return MCPResponse.error("Builtin server not found: " + serverId);
        }

        if (!server.isAvailable()) {
            return MCPResponse.error("Builtin server is not available: " + serverId);
        }

        try {
            return server.executeCapability(capability, params);
        } catch (Exception e) {
            log.error("Error executing capability {} on builtin server {}", capability, serverId, e);
            return MCPResponse.error("Error executing capability: " + e.getMessage());
        }
    }

    /**
     * Obtient un serveur intégré par son ID
     */
    public Optional<BuiltinMCPServer> getServer(String serverId) {
        return Optional.ofNullable(builtinServers.get(serverId));
    }

    /**
     * Obtient la liste de tous les serveurs intégrés
     */
    public List<BuiltinMCPServer> getAllServers() {
        return List.copyOf(builtinServers.values());
    }

    /**
     * Obtient la liste des configurations de serveurs intégrés
     */
    public List<MCPServerConfig> getAllServerConfigs() {
        return builtinServers.values().stream()
                .map(BuiltinMCPServer::getConfig)
                .toList();
    }

    /**
     * Vérifie si un serveur intégré supporte une capacité
     */
    public boolean hasCapability(String serverId, String capability) {
        BuiltinMCPServer server = builtinServers.get(serverId);
        if (server == null) {
            return false;
        }

        MCPServerConfig config = server.getConfig();
        return config.getCapabilities().contains(capability);
    }

    /**
     * Obtient les statistiques des serveurs intégrés
     */
    public BuiltinServerStats getStats() {
        long activeServers = builtinServers.values().stream()
                .mapToLong(server -> server.isAvailable() ? 1 : 0)
                .sum();

        long totalCapabilities = builtinServers.values().stream()
                .mapToLong(server -> server.getConfig().getCapabilities().size())
                .sum();

        return BuiltinServerStats.builder()
                .totalServers(builtinServers.size())
                .activeServers((int) activeServers)
                .totalCapabilities((int) totalCapabilities)
                .build();
    }

    /**
     * Arrête tous les serveurs intégrés
     */
    public void shutdown() {
        log.info("Shutting down builtin MCP servers");

        builtinServers.values().forEach(server -> {
            try {
                server.stop();
                log.debug("Stopped builtin server: {}", server.getId());
            } catch (Exception e) {
                log.warn("Error stopping builtin server: {}", server.getId(), e);
            }
        });

        builtinServers.clear();
        log.info("Builtin MCP servers shutdown complete");
    }

    /**
     * Statistiques des serveurs intégrés
     */
    @lombok.Builder
    @lombok.Value
    public static class BuiltinServerStats {
        int totalServers;
        int activeServers;
        int totalCapabilities;

        public double getAvailabilityRate() {
            return totalServers > 0 ? (double) activeServers / totalServers : 0.0;
        }
    }
}