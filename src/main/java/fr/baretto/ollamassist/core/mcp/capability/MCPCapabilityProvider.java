package fr.baretto.ollamassist.core.mcp.capability;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import fr.baretto.ollamassist.core.mcp.connection.MCPConnectionManager;
import fr.baretto.ollamassist.core.mcp.protocol.MCPMessage;
import fr.baretto.ollamassist.core.mcp.protocol.MCPResponse;
import fr.baretto.ollamassist.core.mcp.server.MCPServerConfig;
import fr.baretto.ollamassist.core.mcp.server.MCPServerRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Fournisseur de capacités MCP
 * Interface principale pour utiliser les capacités des serveurs MCP
 */
@Slf4j
@Service(Service.Level.PROJECT)
public final class MCPCapabilityProvider {

    private final Project project;
    private final MCPConnectionManager connectionManager;
    private final MCPServerRegistry serverRegistry;

    public MCPCapabilityProvider(Project project) {
        this.project = project;
        this.connectionManager = project.getService(MCPConnectionManager.class);
        this.serverRegistry = ApplicationManager.getApplication().getService(MCPServerRegistry.class);
    }

    /**
     * Exécute une capacité sur un serveur MCP spécifique
     */
    public CompletableFuture<MCPResponse> executeCapability(String serverId, String capability, Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Vérifier que le serveur existe et supporte cette capacité
                MCPServerConfig serverConfig = serverRegistry.getServer(serverId)
                        .orElseThrow(() -> new MCPCapabilityException("Server not found: " + serverId));

                if (!serverConfig.hasCapability(capability)) {
                    throw new MCPCapabilityException("Server " + serverId + " does not support capability: " + capability);
                }

                // Créer le message MCP
                MCPMessage message = MCPMessage.methodCall(capability, params);

                // Envoyer le message
                return connectionManager.sendMessage(serverId, message).join();

            } catch (Exception e) {
                log.error("Error executing capability {} on server {}", capability, serverId, e);
                throw new MCPCapabilityException("Failed to execute capability", e);
            }
        });
    }

    /**
     * Trouve le meilleur serveur pour une capacité donnée
     */
    public CompletableFuture<MCPResponse> executeCapability(String capability, Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            List<MCPServerConfig> compatibleServers = serverRegistry.getServersByCapability(capability);

            if (compatibleServers.isEmpty()) {
                throw new MCPCapabilityException("No servers support capability: " + capability);
            }

            // Prendre le premier serveur activé (TODO: améliorer la logique de sélection)
            MCPServerConfig selectedServer = compatibleServers.stream()
                    .filter(MCPServerConfig::isEnabled)
                    .findFirst()
                    .orElseThrow(() -> new MCPCapabilityException("No enabled servers support capability: " + capability));

            return executeCapability(selectedServer.getId(), capability, params).join();
        });
    }

    /**
     * Opérations sur les fichiers
     */
    public CompletableFuture<MCPResponse> readFile(String filePath) {
        return executeCapability("file_read", Map.of("path", filePath));
    }

    public CompletableFuture<MCPResponse> writeFile(String filePath, String content) {
        return executeCapability("file_write", Map.of(
                "path", filePath,
                "content", content
        ));
    }

    public CompletableFuture<MCPResponse> listFiles(String directoryPath) {
        return executeCapability("file_list", Map.of("path", directoryPath));
    }

    /**
     * Opérations Git
     */
    public CompletableFuture<MCPResponse> gitStatus() {
        return executeCapability("git_status", Map.of());
    }

    public CompletableFuture<MCPResponse> gitCommit(String message) {
        return executeCapability("git_commit", Map.of("message", message));
    }

    public CompletableFuture<MCPResponse> gitLog(int limit) {
        return executeCapability("git_log", Map.of("limit", limit));
    }

    /**
     * Opérations de build
     */
    public CompletableFuture<MCPResponse> buildCompile() {
        return executeCapability("build_compile", Map.of());
    }

    public CompletableFuture<MCPResponse> buildTest() {
        return executeCapability("build_test", Map.of());
    }

    public CompletableFuture<MCPResponse> buildClean() {
        return executeCapability("build_clean", Map.of());
    }

    /**
     * Recherche web (si serveur externe configuré)
     */
    public CompletableFuture<MCPResponse> webSearch(String query, int maxResults) {
        return executeCapability("web_search", Map.of(
                "query", query,
                "max_results", maxResults
        ));
    }

    /**
     * Récupération d'URL (si serveur externe configuré)
     */
    public CompletableFuture<MCPResponse> fetchUrl(String url) {
        return executeCapability("url_fetch", Map.of("url", url));
    }

    /**
     * Obtient la liste de toutes les capacités disponibles
     */
    public List<String> getAvailableCapabilities() {
        return serverRegistry.getEnabledServers().stream()
                .flatMap(server -> server.getCapabilities().stream())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Vérifie si une capacité est disponible
     */
    public boolean hasCapability(String capability) {
        return !serverRegistry.getServersByCapability(capability).isEmpty();
    }

    /**
     * Exception spécifique aux capacités MCP
     */
    public static class MCPCapabilityException extends RuntimeException {
        public MCPCapabilityException(String message) {
            super(message);
        }

        public MCPCapabilityException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}