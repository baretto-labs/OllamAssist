package fr.baretto.ollamassist.setting.agent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration pour le mode agent
 */
@Service
@State(name = "AgentModeSettings", storages = @Storage("ollamassist-agent.xml"))
public final class AgentModeSettings implements PersistentStateComponent<AgentModeSettings.State> {

    private State state = new State();

    public static AgentModeSettings getInstance() {
        return ApplicationManager.getApplication().getService(AgentModeSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    // Méthodes de configuration

    public boolean isAgentModeEnabled() {
        return state.agentModeEnabled;
    }

    public void setAgentModeEnabled(boolean enabled) {
        state.agentModeEnabled = enabled;
    }

    public boolean isAutoTaskApprovalEnabled() {
        return state.autoTaskApprovalEnabled;
    }

    public void setAutoTaskApprovalEnabled(boolean enabled) {
        state.autoTaskApprovalEnabled = enabled;
    }

    public int getMaxTasksPerSession() {
        return state.maxTasksPerSession;
    }

    public void setMaxTasksPerSession(int maxTasks) {
        state.maxTasksPerSession = Math.max(1, Math.min(100, maxTasks));
    }

    public boolean isSnapshotEnabled() {
        return state.snapshotEnabled;
    }

    public void setSnapshotEnabled(boolean enabled) {
        state.snapshotEnabled = enabled;
    }

    public boolean isMcpIntegrationEnabled() {
        return state.mcpIntegrationEnabled;
    }

    public void setMcpIntegrationEnabled(boolean enabled) {
        state.mcpIntegrationEnabled = enabled;
    }

    public AgentSecurityLevel getSecurityLevel() {
        return state.securityLevel;
    }

    public void setSecurityLevel(AgentSecurityLevel level) {
        state.securityLevel = level;
    }

    public boolean isTaskProgressUIEnabled() {
        return state.taskProgressUIEnabled;
    }

    public void setTaskProgressUIEnabled(boolean enabled) {
        state.taskProgressUIEnabled = enabled;
    }

    /**
     * Vérifie si une action nécessite une approbation selon le niveau de sécurité
     */
    public boolean requiresApproval(AgentActionType actionType) {
        if (state.autoTaskApprovalEnabled) {
            return false; // Mode expert activé globalement
        }

        return switch (state.securityLevel) {
            case STRICT -> true; // Toutes les actions nécessitent une approbation
            case STANDARD -> actionType.isRisky(); // Seulement les actions risquées
            case EXPERT -> actionType.isHighRisk(); // Seulement les actions très risquées
        };
    }

    /**
     * Restaure les paramètres par défaut
     */
    public void resetToDefaults() {
        state = new State();
    }

    /**
     * Valide la configuration actuelle
     */
    public boolean isConfigurationValid() {
        return state.maxTasksPerSession > 0 &&
                state.maxTasksPerSession <= 100 &&
                state.securityLevel != null;
    }

    /**
     * Vérifie si le mode agent est disponible et prêt à être utilisé
     */
    public boolean isAgentModeAvailable() {
        return state.agentModeEnabled && isConfigurationValid();
    }

    /**
     * Vérifie si une action doit être automatiquement approuvée
     */
    public boolean shouldAutoApprove(AgentActionType actionType) {
        if (!state.agentModeEnabled) {
            return false;
        }

        if (state.autoTaskApprovalEnabled) {
            return true; // Mode expert global activé
        }

        return switch (state.securityLevel) {
            case STRICT -> false; // Aucune auto-approbation
            case STANDARD -> !actionType.isRisky(); // Seulement actions sûres
            case EXPERT -> !actionType.isHighRisk(); // Éviter seulement les actions très risquées
        };
    }

    /**
     * Active le mode agent avec configuration recommandée
     */
    public void enableAgentMode() {
        state.agentModeEnabled = true;
        state.securityLevel = AgentSecurityLevel.STANDARD;
        state.maxTasksPerSession = 10;
        state.autoTaskApprovalEnabled = false;
        state.snapshotEnabled = true;
        state.taskProgressUIEnabled = true;
    }

    /**
     * Désactive le mode agent
     */
    public void disableAgentMode() {
        state.agentModeEnabled = false;
    }

    /**
     * Obtient un résumé de la configuration
     */
    public String getConfigurationSummary() {
        return String.format(
                "Sécurité: %s • Max: %d tâches • Auto-approbation: %s",
                state.securityLevel.name(),
                state.maxTasksPerSession,
                state.autoTaskApprovalEnabled ? "Oui" : "Non"
        );
    }

    /**
     * Niveaux de sécurité pour le mode agent
     */
    public enum AgentSecurityLevel {
        /**
         * Mode strict - toutes les actions nécessitent une approbation
         */
        STRICT("Strict - Toutes les actions nécessitent une approbation"),

        /**
         * Mode standard - actions sûres automatiques, actions risquées nécessitent approbation
         */
        STANDARD("Standard - Actions sûres automatiques"),

        /**
         * Mode expert - la plupart des actions sont automatiques
         */
        EXPERT("Expert - La plupart des actions sont automatiques");

        private final String description;

        AgentSecurityLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * Types d'actions agent pour évaluation de sécurité
     */
    public enum AgentActionType {
        // Actions de lecture (sûres)
        READ_FILE(false, false),
        LIST_DIRECTORY(false, false),
        GET_FILE_INFO(false, false),
        WEB_SEARCH(false, false),

        // Actions d'écriture (risquées)
        WRITE_FILE(true, false),
        CREATE_DIRECTORY(true, false),
        COPY_FILE(true, false),

        // Actions destructives (très risquées)
        DELETE_FILE(true, true),
        MOVE_FILE(true, false),
        GIT_COMMIT(true, false),
        GIT_PUSH(true, true),
        BUILD_OPERATION(true, false),

        // Actions système (très risquées)
        EXECUTE_COMMAND(true, true),
        MODIFY_SETTINGS(true, true);

        private final boolean risky;
        private final boolean highRisk;

        AgentActionType(boolean risky, boolean highRisk) {
            this.risky = risky;
            this.highRisk = highRisk;
        }

        public boolean isRisky() {
            return risky;
        }

        public boolean isHighRisk() {
            return highRisk;
        }
    }

    /**
     * État persistant des paramètres agent
     */
    @Data
    public static class State {
        // Activation générale du mode agent
        public boolean agentModeEnabled = true;

        // Auto-approbation des tâches (mode expert)
        public boolean autoTaskApprovalEnabled = false;

        // Nombre maximum de tâches par session
        public int maxTasksPerSession = 10;

        // Activation des snapshots pour rollback
        public boolean snapshotEnabled = true;

        // Intégration MCP
        public boolean mcpIntegrationEnabled = true;

        // Niveau de sécurité
        public AgentSecurityLevel securityLevel = AgentSecurityLevel.STANDARD;

        // Interface utilisateur pour progression des tâches
        public boolean taskProgressUIEnabled = true;
    }
}