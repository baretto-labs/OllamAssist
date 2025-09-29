package fr.baretto.ollamassist.core.agent;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import fr.baretto.ollamassist.events.NewUserMessageNotifier;
import lombok.extern.slf4j.Slf4j;


/**
 * Intégration entre le système de chat et l'agent - Point d'entrée unifié pour tous les messages
 */
@Slf4j
public class AgentChatIntegration {

    private final Project project;
    private final AgentCoordinator agentCoordinator;
    private final MessageBusConnection messageBusConnection;
    private ChatFallbackHandler fallbackHandler;

    public AgentChatIntegration(Project project, AgentCoordinator agentCoordinator) {
        this.project = project;
        this.agentCoordinator = agentCoordinator;

        // S'abonner aux nouveaux messages utilisateur
        this.messageBusConnection = project.getMessageBus().connect();
        this.messageBusConnection.subscribe(NewUserMessageNotifier.TOPIC, this::onNewUserMessage);

        log.info("AgentChatIntegration initialized - all messages will be routed through agent");
    }

    /**
     * Traite un nouveau message utilisateur via l'agent unifié avec streaming
     */
    public void onNewUserMessage(String message) {
        log.info("Routing message to unified agent with streaming: {}", message);

        try {
            log.error("🔍 DEBUG: AgentChatIntegration.onNewUserMessage called with: {}", message);


            // Utiliser directement AgentService pour le streaming
            AgentService agentService = project.getService(AgentService.class);
            if (agentService != null) {
                log.error("🔍 DEBUG: AgentService found, calling executeUserRequestWithStreaming");
                agentService.executeUserRequestWithStreaming(message);
            } else {
                log.error("🔍 DEBUG: AgentService not available - this should not happen");
            }
        } catch (Exception e) {
            log.error("🔍 DEBUG: Error processing message through agent", e);
        }
    }

    /**
     * Configure le handler de fallback pour les tests
     */
    public void setChatFallbackHandler(ChatFallbackHandler handler) {
        this.fallbackHandler = handler;
    }

    /**
     * Vérifie si l'agent est disponible
     */
    public boolean isAgentActive() {
        return agentCoordinator != null && !agentCoordinator.isBusy();
    }

    public void dispose() {
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
    }

    /**
     * Interface pour le fallback
     */
    public interface ChatFallbackHandler {
        void processUserMessage(String message);
    }
}