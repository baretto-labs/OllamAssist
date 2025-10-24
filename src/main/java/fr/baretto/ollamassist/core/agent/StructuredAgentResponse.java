package fr.baretto.ollamassist.core.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Structured output pour l'agent ReAct
 * Alternative aux LangChain4J tools quand Ollama ne supporte pas le function calling
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuredAgentResponse {

    @JsonProperty("thinking")
    private String thinking;

    @JsonProperty("action")
    private AgentAction action;

    @JsonProperty("observation")
    private String observation;

    @JsonProperty("final_answer")
    private String finalAnswer;

    @JsonProperty("continue_cycle")
    private boolean continueCycle;

    /**
     * Vérifie si cette réponse contient une action à exécuter
     */
    public boolean hasAction() {
        return action != null && action.getTool() != null && !action.getTool().trim().isEmpty();
    }

    /**
     * Vérifie si le cycle ReAct doit continuer
     */
    public boolean shouldContinue() {
        return continueCycle && !hasCompleteFinalAnswer();
    }

    /**
     * Vérifie si on a une réponse finale (même partielle)
     */
    public boolean hasFinalAnswer() {
        return finalAnswer != null && !finalAnswer.trim().isEmpty();
    }

    /**
     * Vérifie si on a une réponse finale complète
     */
    public boolean hasCompleteFinalAnswer() {
        return finalAnswer != null && !finalAnswer.trim().isEmpty() && !continueCycle;
    }

    /**
     * Récupère le contenu à afficher à l'utilisateur
     */
    public String getUserContent() {
        if (hasCompleteFinalAnswer()) {
            return finalAnswer;
        }
        if (thinking != null && !thinking.trim().isEmpty()) {
            return "🤔 " + thinking;
        }
        if (action != null && action.getReasoning() != null) {
            return "⚡ " + action.getReasoning();
        }
        return "🔄 Processing...";
    }

    /**
     * Récupère le statut actuel pour l'UI
     */
    public String getStatusMessage() {
        if (hasAction()) {
            return "Executing: " + action.getTool();
        }
        if (hasCompleteFinalAnswer()) {
            return "Completed";
        }
        if (thinking != null && !thinking.trim().isEmpty()) {
            return "Thinking...";
        }
        return "Processing...";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentAction {
        @JsonProperty("tool")
        private String tool;

        @JsonProperty("parameters")
        private Map<String, Object> parameters;

        @JsonProperty("reasoning")
        private String reasoning;
    }
}