package fr.baretto.ollamassist.core.events;

import com.intellij.util.messages.Topic;
import fr.baretto.ollamassist.core.state.PluginState;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Event topics and listeners for OllamAssist using IntelliJ's MessageBus system.
 * Defines all communication events between components.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OllamAssistEvents {

    /**
     * Topic for completion request events.
     */
    public static final Topic<CompletionRequestListener> COMPLETION_REQUESTED =
            Topic.create("OllamAssist.CompletionRequested", CompletionRequestListener.class);

    /**
     * Topic for chat request events.
     */
    public static final Topic<ChatRequestListener> CHAT_REQUESTED =
            Topic.create("OllamAssist.ChatRequested", ChatRequestListener.class);

    /**
     * Topic for commit message request events.
     */
    public static final Topic<CommitMessageRequestListener> COMMIT_MESSAGE_REQUESTED =
            Topic.create("OllamAssist.CommitMessageRequested", CommitMessageRequestListener.class);

    /**
     * Topic for state change events.
     */
    public static final Topic<StateChangeListener> STATE_CHANGED =
            Topic.create("OllamAssist.StateChanged", StateChangeListener.class);

    /**
     * Listener for code completion requests.
     */
    public interface CompletionRequestListener {
        void onCompletionRequested(String context, String fileExtension);
    }

    /**
     * Listener for chat requests.
     */
    public interface ChatRequestListener {
        void onChatRequested(String message);
    }

    /**
     * Listener for commit message generation requests.
     */
    public interface CommitMessageRequestListener {
        void onCommitMessageRequested(String gitDiff);
    }

    /**
     * Listener for state change events.
     */
    public interface StateChangeListener {
        void onStateChanged(PluginState previousState, PluginState newState);
    }

}