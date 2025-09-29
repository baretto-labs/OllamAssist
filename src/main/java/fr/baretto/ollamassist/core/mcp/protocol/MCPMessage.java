package fr.baretto.ollamassist.core.mcp.protocol;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Représente un message MCP selon le protocole Model Context Protocol
 */
@Value
@Builder
public class MCPMessage {
    String id;
    String method;
    Map<String, Object> params;
    String jsonrpc = "2.0";

    /**
     * Crée un message d'appel de méthode
     */
    public static MCPMessage methodCall(String method, Map<String, Object> params) {
        return MCPMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .method(method)
                .params(params)
                .build();
    }

    /**
     * Crée un message d'appel de méthode simple
     */
    public static MCPMessage methodCall(String method) {
        return methodCall(method, Map.of());
    }

    /**
     * Parse un message depuis JSON (placeholder)
     */
    public static MCPMessage fromJson(String json) {
        // TODO: Implémenter avec Jackson
        throw new UnsupportedOperationException("JSON parsing not implemented yet");
    }

    /**
     * Obtient un paramètre typé
     */
    @SuppressWarnings("unchecked")
    public <T> T getParam(String key, Class<T> type) {
        if (params == null) return null;

        Object value = params.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    /**
     * Vérifie si le message contient un paramètre
     */
    public boolean hasParam(String key) {
        return params != null && params.containsKey(key);
    }

    /**
     * Convertit le message en JSON (placeholder)
     */
    public String toJson() {
        // TODO: Implémenter avec Jackson
        return String.format(
                "{\"jsonrpc\":\"%s\",\"id\":\"%s\",\"method\":\"%s\",\"params\":%s}",
                jsonrpc, id, method, paramsToJson()
        );
    }

    private String paramsToJson() {
        if (params == null || params.isEmpty()) {
            return "{}";
        }
        // Simplification pour l'instant
        return params.toString();
    }
}