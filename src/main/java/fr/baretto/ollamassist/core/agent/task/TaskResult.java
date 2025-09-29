package fr.baretto.ollamassist.core.agent.task;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Résultat de l'exécution d'une tâche
 */
@Value
@Builder
public class TaskResult {
    boolean success;
    String message;
    String errorMessage;
    Map<String, Object> data;
    LocalDateTime timestamp;
    Duration executionTime;
    String taskId;

    /**
     * Crée un résultat de succès
     */
    public static TaskResult success(String message) {
        return TaskResult.builder()
                .success(true)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Crée un résultat de succès avec données
     */
    public static TaskResult success(String message, Map<String, Object> data) {
        return TaskResult.builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Crée un résultat d'échec
     */
    public static TaskResult failure(String errorMessage) {
        return TaskResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Crée un résultat d'échec avec exception
     */
    public static TaskResult failure(String errorMessage, Throwable throwable) {
        return TaskResult.builder()
                .success(false)
                .errorMessage(errorMessage + ": " + throwable.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Obtient une donnée typée du résultat
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> type) {
        if (data == null) return null;

        Object value = data.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    /**
     * Vérifie si le résultat contient des données
     */
    public boolean hasData() {
        return data != null && !data.isEmpty();
    }

    /**
     * Obtient un message formaté pour l'affichage
     */
    public String getDisplayMessage() {
        if (success) {
            return message != null ? message : "Tâche exécutée avec succès";
        } else {
            return errorMessage != null ? errorMessage : "Échec de la tâche";
        }
    }
}