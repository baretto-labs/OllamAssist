package fr.baretto.ollamassist.core.mcp.server;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Configuration d'un serveur MCP
 */
@Value
@Builder
public class MCPServerConfig {
    String id;
    String name;
    MCPServerType type;
    boolean enabled;
    String endpoint;
    MCPAuth auth;
    List<String> capabilities;
    Map<String, Object> config;
    long timeout;

    /**
     * Crée une configuration pour un serveur intégré
     */
    public static MCPServerConfig builtin(String id, String name, List<String> capabilities) {
        return MCPServerConfig.builder()
                .id(id)
                .name(name)
                .type(MCPServerType.BUILTIN)
                .enabled(true)
                .capabilities(capabilities)
                .timeout(30000)
                .auth(MCPAuth.builder().type(MCPAuth.MCPAuthType.NONE).build())
                .build();
    }

    /**
     * Crée une configuration pour un serveur HTTP
     */
    public static MCPServerConfig http(String id, String name, String endpoint, List<String> capabilities) {
        return MCPServerConfig.builder()
                .id(id)
                .name(name)
                .type(MCPServerType.HTTP)
                .endpoint(endpoint)
                .enabled(true)
                .capabilities(capabilities)
                .timeout(30000)
                .auth(MCPAuth.builder().type(MCPAuth.MCPAuthType.NONE).build())
                .build();
    }

    /**
     * Crée une configuration pour un serveur WebSocket
     */
    public static MCPServerConfig webSocket(String id, String name, String endpoint, List<String> capabilities) {
        return MCPServerConfig.builder()
                .id(id)
                .name(name)
                .type(MCPServerType.WEBSOCKET)
                .endpoint(endpoint)
                .enabled(true)
                .capabilities(capabilities)
                .timeout(30000)
                .auth(MCPAuth.builder().type(MCPAuth.MCPAuthType.NONE).build())
                .build();
    }

    /**
     * Vérifie si le serveur supporte une capacité
     */
    public boolean hasCapability(String capability) {
        return capabilities != null && capabilities.contains(capability);
    }

    /**
     * Obtient une valeur de configuration
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String key, Class<T> type) {
        if (config == null) return null;

        Object value = config.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    /**
     * Vérifie si la configuration est valide
     */
    public boolean isValid() {
        if (id == null || id.isEmpty() || name == null || name.isEmpty()) {
            return false;
        }

        return switch (type) {
            case BUILTIN -> true;
            case HTTP, WEBSOCKET -> endpoint != null && !endpoint.isEmpty();
        };
    }

    /**
     * Types de serveurs MCP supportés
     */
    public enum MCPServerType {
        BUILTIN,    // Serveur intégré au plugin
        HTTP,       // Serveur HTTP/REST
        WEBSOCKET   // Serveur WebSocket
    }

    /**
     * Configuration d'authentification
     */
    @Value
    @Builder
    public static class MCPAuth {
        MCPAuthType type;
        String key;
        String keyEnv;
        String username;
        String password;
        Map<String, String> headers;

        /**
         * Obtient la clé d'API depuis l'environnement ou directement
         */
        public String getApiKey() {
            if (keyEnv != null && !keyEnv.isEmpty()) {
                return System.getenv(keyEnv);
            }
            return key;
        }

        public enum MCPAuthType {
            NONE,
            API_KEY,
            BASIC_AUTH,
            OAUTH2,
            CUSTOM
        }
    }
}