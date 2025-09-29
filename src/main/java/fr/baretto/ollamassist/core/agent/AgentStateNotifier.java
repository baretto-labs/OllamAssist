package fr.baretto.ollamassist.core.agent;

import com.intellij.util.messages.Topic;

/**
 * Notificateur pour les changements d'état de l'agent
 */
public interface AgentStateNotifier {

    Topic<AgentStateNotifier> TOPIC = Topic.create(
            "AgentStateNotifier",
            AgentStateNotifier.class
    );

    /**
     * Appelé quand l'état de l'agent change
     */
    void stateChanged(AgentCoordinator.AgentState oldState, AgentCoordinator.AgentState newState);
}