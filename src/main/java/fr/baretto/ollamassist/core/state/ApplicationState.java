package fr.baretto.ollamassist.core.state;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.messages.MessageBus;
import fr.baretto.ollamassist.core.events.OllamAssistEvents;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Centralized application state management with thread-safe operations.
 * Publishes state change events via IntelliJ MessageBus.
 * IntelliJ Application Service - singleton across the entire application.
 */
@Slf4j
@Service
public final class ApplicationState {

    private final MessageBus messageBus;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<StateTransition> stateHistory = new ArrayList<>();

    private volatile PluginState currentState;
    private int maxHistorySize = 50;

    public ApplicationState() {
        this(ApplicationManager.getApplication() != null ?
                ApplicationManager.getApplication().getMessageBus() : null);
    }

    // Constructor for testing - package-private
    public ApplicationState(MessageBus messageBus) {
        this.messageBus = messageBus;
        this.currentState = PluginState.IDLE;

        // Record initial state
        lock.writeLock().lock();
        try {
            stateHistory.add(new StateTransition(PluginState.IDLE, LocalDateTime.now()));
        } finally {
            lock.writeLock().unlock();
        }

        log.debug("ApplicationState initialized with state: {}", currentState);
    }

    /**
     * Sets the current plugin state and publishes change event if different.
     *
     * @param newState The new state to set
     */
    public void setState(@NotNull PluginState newState) {
        PluginState previousState;
        boolean stateChanged;

        lock.writeLock().lock();
        try {
            previousState = this.currentState;
            stateChanged = previousState != newState;

            if (stateChanged) {
                this.currentState = newState;

                // Add to history
                stateHistory.add(new StateTransition(newState, LocalDateTime.now()));

                // Limit history size
                while (stateHistory.size() > maxHistorySize) {
                    stateHistory.remove(0);
                }

                log.debug("State changed: {} -> {}", previousState, newState);
            }
        } finally {
            lock.writeLock().unlock();
        }

        // Publish event outside of lock to avoid deadlock
        if (stateChanged) {
            publishStateChangeEvent(previousState, newState);
        }
    }

    /**
     * Gets the current plugin state.
     *
     * @return The current state
     */
    public PluginState getCurrentState() {
        return currentState;
    }

    /**
     * Checks if the plugin is currently processing.
     *
     * @return true if in PROCESSING state
     */
    public boolean isProcessing() {
        return currentState == PluginState.PROCESSING;
    }

    /**
     * Checks if the plugin is in agent mode.
     *
     * @return true if in AGENT_MODE state
     */
    public boolean isAgentMode() {
        return currentState == PluginState.AGENT_MODE;
    }

    /**
     * Gets the state transition history.
     *
     * @return Immutable list of state transitions
     */
    public List<StateTransition> getStateHistory() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(stateHistory);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Sets the maximum state history size.
     *
     * @param maxHistorySize Maximum number of state transitions to keep
     */
    public void setMaxHistorySize(int maxHistorySize) {
        if (maxHistorySize <= 0) {
            throw new IllegalArgumentException("Max history size must be positive");
        }

        lock.writeLock().lock();
        try {
            this.maxHistorySize = maxHistorySize;

            // Trim history if needed
            while (stateHistory.size() > maxHistorySize) {
                stateHistory.remove(0);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets state statistics.
     *
     * @return State statistics
     */
    public StateStats getStateStats() {
        lock.readLock().lock();
        try {
            Map<PluginState, Integer> stateCounts = new EnumMap<>(PluginState.class);

            for (StateTransition transition : stateHistory) {
                stateCounts.merge(transition.getState(), 1, Integer::sum);
            }

            return new StateStats(stateHistory.size(), stateCounts);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Resets the application state to IDLE and clears history.
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            PluginState previousState = this.currentState;
            this.currentState = PluginState.IDLE;

            // Clear and reinitialize history
            stateHistory.clear();
            stateHistory.add(new StateTransition(PluginState.IDLE, LocalDateTime.now()));

            log.debug("ApplicationState reset to IDLE");

            // Publish event if state actually changed
            if (previousState != PluginState.IDLE) {
                publishStateChangeEvent(previousState, PluginState.IDLE);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void publishStateChangeEvent(PluginState previousState, PluginState newState) {
        if (messageBus == null) {
            return; // Skip publishing in test environment
        }
        try {
            messageBus.syncPublisher(OllamAssistEvents.STATE_CHANGED)
                    .onStateChanged(previousState, newState);
        } catch (Exception e) {
            log.error("Error publishing state change event: {} -> {}", previousState, newState, e);
        }
    }

    /**
     * Represents a state transition with timestamp.
     */
    public static class StateTransition {
        private final PluginState state;
        private final LocalDateTime timestamp;

        public StateTransition(PluginState state, LocalDateTime timestamp) {
            this.state = state;
            this.timestamp = timestamp;
        }

        public PluginState getState() {
            return state;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Statistics about state transitions.
     */
    public static class StateStats {
        private final int totalTransitions;
        private final Map<PluginState, Integer> stateTransitionCounts;

        public StateStats(int totalTransitions, Map<PluginState, Integer> stateTransitionCounts) {
            this.totalTransitions = totalTransitions;
            this.stateTransitionCounts = new EnumMap<>(stateTransitionCounts);
        }

        public int getTotalTransitions() {
            return totalTransitions;
        }

        public int getTransitionsToState(PluginState state) {
            return stateTransitionCounts.getOrDefault(state, 0);
        }

        public Map<PluginState, Integer> getAllTransitionCounts() {
            return new EnumMap<>(stateTransitionCounts);
        }
    }
}