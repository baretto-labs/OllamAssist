package fr.baretto.ollamassist.core.state;

/**
 * Enumeration of possible plugin states for centralized state management.
 */
public enum PluginState {

    /**
     * Plugin is idle, waiting for user interactions.
     */
    IDLE,

    /**
     * Plugin is processing a request (completion, chat, etc.).
     */
    PROCESSING,

    /**
     * Plugin is in agent mode, handling multi-step autonomous tasks.
     */
    AGENT_MODE
}