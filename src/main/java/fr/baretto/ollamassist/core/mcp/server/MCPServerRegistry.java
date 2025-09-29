package fr.baretto.ollamassist.core.mcp.server;

import com.intellij.openapi.components.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registre des serveurs MCP disponibles
 * Gère la configuration et la découverte des serveurs MCP
 */
@Slf4j
@Service(Service.Level.APP)
public final class MCPServerRegistry {

    private final ConcurrentMap<String, MCPServerConfig> registeredServers = new ConcurrentHashMap<>();

    public MCPServerRegistry() {
        initializeBuiltinServers();
        log.info("MCPServerRegistry initialized with {} builtin servers", registeredServers.size());
    }

    /**
     * Enregistre un serveur MCP
     */
    public void registerServer(MCPServerConfig serverConfig) {
        if (!serverConfig.isValid()) {
            throw new IllegalArgumentException("Invalid server configuration: " + serverConfig.getId());
        }

        registeredServers.put(serverConfig.getId(), serverConfig);
        log.info("Registered MCP server: {} ({})", serverConfig.getName(), serverConfig.getType());
    }

    /**
     * Supprime un serveur du registre
     */
    public boolean unregisterServer(String serverId) {
        MCPServerConfig removed = registeredServers.remove(serverId);
        if (removed != null) {
            log.info("Unregistered MCP server: {}", removed.getName());
            return true;
        }
        return false;
    }

    /**
     * Obtient un serveur par son ID
     */
    public Optional<MCPServerConfig> getServer(String serverId) {
        return Optional.ofNullable(registeredServers.get(serverId));
    }

    /**
     * Obtient tous les serveurs enregistrés
     */
    public List<MCPServerConfig> getAllServers() {
        return new ArrayList<>(registeredServers.values());
    }

    /**
     * Obtient les serveurs activés
     */
    public List<MCPServerConfig> getEnabledServers() {
        return registeredServers.values().stream()
                .filter(MCPServerConfig::isEnabled)
                .toList();
    }

    /**
     * Obtient les serveurs par type
     */
    public List<MCPServerConfig> getServersByType(MCPServerConfig.MCPServerType type) {
        return registeredServers.values().stream()
                .filter(server -> server.getType() == type)
                .toList();
    }

    /**
     * Recherche des serveurs par capacité
     */
    public List<MCPServerConfig> getServersByCapability(String capability) {
        return registeredServers.values().stream()
                .filter(server -> server.hasCapability(capability))
                .toList();
    }

    /**
     * Met à jour la configuration d'un serveur
     */
    public boolean updateServer(MCPServerConfig updatedConfig) {
        if (!updatedConfig.isValid()) {
            log.warn("Cannot update server with invalid configuration: {}", updatedConfig.getId());
            return false;
        }

        if (registeredServers.containsKey(updatedConfig.getId())) {
            registeredServers.put(updatedConfig.getId(), updatedConfig);
            log.info("Updated MCP server configuration: {}", updatedConfig.getName());
            return true;
        }

        log.warn("Cannot update non-existent server: {}", updatedConfig.getId());
        return false;
    }

    /**
     * Vérifie si un serveur existe
     */
    public boolean hasServer(String serverId) {
        return registeredServers.containsKey(serverId);
    }

    /**
     * Obtient le nombre de serveurs enregistrés
     */
    public int getServerCount() {
        return registeredServers.size();
    }

    /**
     * Obtient les statistiques du registre
     */
    public RegistryStats getStats() {
        Map<MCPServerConfig.MCPServerType, Long> serversByType = new HashMap<>();
        for (MCPServerConfig server : registeredServers.values()) {
            serversByType.merge(server.getType(), 1L, Long::sum);
        }

        long enabledCount = registeredServers.values().stream()
                .mapToLong(server -> server.isEnabled() ? 1 : 0)
                .sum();

        return RegistryStats.builder()
                .totalServers(registeredServers.size())
                .enabledServers((int) enabledCount)
                .disabledServers(registeredServers.size() - (int) enabledCount)
                .serversByType(serversByType)
                .build();
    }

    private void initializeBuiltinServers() {
        // NOTE: These builtin MCP servers are currently demonstration implementations only
        // They are NOT functional and return only simulation messages
        // They are disabled by default until real implementations are completed

        // Builtin file system server (DEMO ONLY)
        registerServer(MCPServerConfig.builder()
                .id("filesystem")
                .name("File System Operations (Demo - Non-functional)")
                .type(MCPServerConfig.MCPServerType.BUILTIN)
                .enabled(false) // Disabled - demo implementation only
                .capabilities(List.of("file_read", "file_write", "file_list", "directory_create"))
                .timeout(30000)
                .auth(MCPServerConfig.MCPAuth.builder().type(MCPServerConfig.MCPAuth.MCPAuthType.NONE).build())
                .build());

        // Builtin Git server (DEMO ONLY)
        registerServer(MCPServerConfig.builder()
                .id("git")
                .name("Git Operations (Demo - Non-functional)")
                .type(MCPServerConfig.MCPServerType.BUILTIN)
                .enabled(false) // Disabled - demo implementation only
                .capabilities(List.of("git_status", "git_commit", "git_branch", "git_log"))
                .timeout(30000)
                .auth(MCPServerConfig.MCPAuth.builder().type(MCPServerConfig.MCPAuth.MCPAuthType.NONE).build())
                .build());

        // Builtin build server (DEMO ONLY)
        registerServer(MCPServerConfig.builder()
                .id("build")
                .name("Build Operations (Demo - Non-functional)")
                .type(MCPServerConfig.MCPServerType.BUILTIN)
                .enabled(false) // Disabled - demo implementation only
                .capabilities(List.of("build_compile", "build_test", "build_clean"))
                .timeout(30000)
                .auth(MCPServerConfig.MCPAuth.builder().type(MCPServerConfig.MCPAuth.MCPAuthType.NONE).build())
                .build());
    }

    /**
     * Statistiques du registre de serveurs
     */
    @lombok.Builder
    @lombok.Value
    public static class RegistryStats {
        int totalServers;
        int enabledServers;
        int disabledServers;
        Map<MCPServerConfig.MCPServerType, Long> serversByType;
    }
}