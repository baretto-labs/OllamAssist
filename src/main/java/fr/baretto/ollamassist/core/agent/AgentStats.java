package fr.baretto.ollamassist.core.agent;

import lombok.Builder;
import lombok.Value;

/**
 * Statistiques de l'agent
 */
@Value
@Builder
public class AgentStats {
    int activeTasksCount;
    AgentCoordinator.AgentState currentState;
    long totalTasksExecuted;
    double successRate;
    long averageExecutionTime;
    long lastActivityTimestamp;

    public String getFormattedSuccessRate() {
        return String.format("%.1f%%", successRate * 100);
    }

    public boolean isHealthy() {
        return successRate > 0.8 && currentState != AgentCoordinator.AgentState.ERROR;
    }
}