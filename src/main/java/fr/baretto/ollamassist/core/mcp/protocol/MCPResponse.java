package fr.baretto.ollamassist.core.mcp.protocol;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Représente une réponse MCP selon le protocole Model Context Protocol
 */
@Value
@Builder
public class MCPResponse {
    String id;
    Object result;
    MCPError error;
    String jsonrpc = "2.0";

    /**
     * Crée une réponse de succès
     */
    public static MCPResponse success(Object result) {
        return MCPResponse.builder()
                .id(java.util.UUID.randomUUID().toString())
                .result(result)
                .build();
    }

    /**
     * Crée une réponse d'erreur
     */
    public static MCPResponse error(String message) {
        return MCPResponse.builder()
                .id(java.util.UUID.randomUUID().toString())
                .error(MCPError.builder()
                        .code(-1)
                        .message(message)
                        .build())
                .build();
    }

    /**
     * Crée une réponse d'erreur avec code
     */
    public static MCPResponse error(int code, String message) {
        return MCPResponse.builder()
                .id(java.util.UUID.randomUUID().toString())
                .error(MCPError.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }

    /**
     * Parse une réponse depuis JSON (placeholder)
     */
    public static MCPResponse fromJson(String json) {
        // TODO: Implémenter avec Jackson
        throw new UnsupportedOperationException("JSON parsing not implemented yet");
    }

    /**
     * Vérifie si la réponse indique un succès
     */
    public boolean isSuccess() {
        return error == null;
    }

    /**
     * Obtient le résultat typé
     */
    @SuppressWarnings("unchecked")
    public <T> T getResult(Class<T> type) {
        if (result != null && type.isAssignableFrom(result.getClass())) {
            return (T) result;
        }
        return null;
    }

    /**
     * Convertit la réponse en JSON (placeholder)
     */
    public String toJson() {
        // TODO: Implémenter avec Jackson
        if (isSuccess()) {
            return String.format(
                    "{\"jsonrpc\":\"%s\",\"id\":\"%s\",\"result\":%s}",
                    jsonrpc, id, result
            );
        } else {
            return String.format(
                    "{\"jsonrpc\":\"%s\",\"id\":\"%s\",\"error\":{\"code\":%d,\"message\":\"%s\"}}",
                    jsonrpc, id, error.getCode(), error.getMessage()
            );
        }
    }

    /**
     * Représente une erreur MCP
     */
    @Value
    @Builder
    public static class MCPError {
        int code;
        String message;
        Map<String, Object> data;
    }
}