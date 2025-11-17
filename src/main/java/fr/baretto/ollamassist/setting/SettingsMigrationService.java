package fr.baretto.ollamassist.setting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Service responsible for migrating settings from the legacy OllamAssistSettings
 * to the new split settings classes (OllamaSettings, RAGSettings, ActionsSettings, UISettings).
 *
 * This migration runs once per installation when the plugin is first loaded.
 */
@Slf4j
@State(
        name = "SettingsMigration",
        storages = {@Storage("SettingsMigration.xml")}
)
public class SettingsMigrationService implements PersistentStateComponent<SettingsMigrationService.State> {

    private State myState = new State();

    public static SettingsMigrationService getInstance() {
        return ApplicationManager.getApplication().getService(SettingsMigrationService.class);
    }

    @Nullable
    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    /**
     * Performs the migration from OllamAssistSettings to new settings classes.
     * This method is idempotent and will only execute once.
     */
    public void migrateIfNeeded() {
        if (myState.migrationCompleted) {
            log.debug("Settings migration already completed, skipping");
            return;
        }

        performMigration();
    }

    /**
     * Forces migration even if it was already completed.
     * Useful for development or fixing migration issues.
     */
    public void forceMigration() {
        log.warn("Forcing migration (bypassing completion check)");
        myState.migrationCompleted = false;
        performMigration();
    }

    private void performMigration() {

        log.info("Starting settings migration from OllamAssistSettings to new settings classes");

        try {
            OllamAssistSettings legacySettings = OllamAssistSettings.getInstance();
            OllamAssistSettings.State legacyState = legacySettings.getState();

            if (legacyState == null) {
                log.warn("Legacy settings state is null, skipping migration");
                markMigrationCompleted();
                return;
            }

            // Migrate to OllamaSettings
            migrateOllamaSettings(legacyState);

            // Migrate to RAGSettings
            migrateRAGSettings(legacyState);

            // Migrate to ActionsSettings
            migrateActionsSettings(legacyState);

            // Migrate to UISettings
            migrateUISettings(legacyState);

            markMigrationCompleted();
            log.info("Settings migration completed successfully");

        } catch (Exception e) {
            log.error("Error during settings migration", e);
            // Don't mark as completed so it will retry next time
        }
    }

    private void migrateOllamaSettings(OllamAssistSettings.State legacyState) {
        OllamaSettings ollamaSettings = OllamaSettings.getInstance();
        OllamaSettings.State currentState = ollamaSettings.getState();

        if (currentState == null) {
            log.warn("OllamaSettings state is null, cannot migrate");
            return;
        }

        log.info("Migrating Ollama settings field by field");
        int migratedFields = 0;

        // Migrate each field independently if it's still at default value
        if (OllamaSettings.DEFAULT_URL.equals(currentState.chatOllamaUrl)) {
            ollamaSettings.setChatOllamaUrl(legacyState.chatOllamaUrl);
            migratedFields++;
        }
        if (OllamaSettings.DEFAULT_URL.equals(currentState.completionOllamaUrl)) {
            ollamaSettings.setCompletionOllamaUrl(legacyState.completionOllamaUrl);
            migratedFields++;
        }
        if (OllamaSettings.DEFAULT_URL.equals(currentState.embeddingOllamaUrl)) {
            ollamaSettings.setEmbeddingOllamaUrl(legacyState.embeddingOllamaUrl);
            migratedFields++;
        }
        // Don't migrate model names - let users configure them manually
        // Models are now configured per-purpose (chat vs completion) and users should choose compatible models
        // Note: Migration is skipped for chat and completion models to avoid breaking existing configs
        if (currentState.embeddingModelName == null || currentState.embeddingModelName.isEmpty()) {
            ollamaSettings.setEmbeddingModelName(legacyState.embeddingModelName);
            migratedFields++;
        }
        if ("300".equals(currentState.timeout)) {
            ollamaSettings.setTimeout(legacyState.timeout);
            migratedFields++;
        }
        if (currentState.username == null || currentState.username.isEmpty()) {
            ollamaSettings.setUsername(legacyState.username);
            migratedFields++;
        }
        if (currentState.password == null || currentState.password.isEmpty()) {
            ollamaSettings.setPassword(legacyState.password);
            migratedFields++;
        }

        log.info("Ollama settings migrated successfully ({} fields migrated)", migratedFields);
    }

    private void migrateRAGSettings(OllamAssistSettings.State legacyState) {
        RAGSettings ragSettings = RAGSettings.getInstance();
        RAGSettings.State currentState = ragSettings.getState();

        if (currentState == null) {
            log.warn("RAGSettings state is null, cannot migrate");
            return;
        }

        // Only migrate if the new settings are still at default values
        if (isDefaultRAGSettings(currentState)) {
            log.info("Migrating RAG settings");
            ragSettings.setSources(legacyState.sources);
            ragSettings.setIndexationSize(legacyState.indexationSize);
            ragSettings.setWebSearchEnabled(legacyState.webSearchEnabled);
            ragSettings.setRAGEnabled(legacyState.ragEnabled);
            log.info("RAG settings migrated successfully");
        } else {
            log.info("RAGSettings already has custom values, skipping migration");
        }
    }

    private void migrateActionsSettings(OllamAssistSettings.State legacyState) {
        ActionsSettings actionsSettings = ActionsSettings.getInstance();
        ActionsSettings.State currentState = actionsSettings.getState();

        if (currentState == null) {
            log.warn("ActionsSettings state is null, cannot migrate");
            return;
        }

        // Only migrate if the new settings are still at default values
        if (isDefaultActionsSettings(currentState)) {
            log.info("Migrating Actions settings");
            actionsSettings.setAutoApproveFileCreation(legacyState.autoApproveFileCreation);
            log.info("Actions settings migrated successfully");
        } else {
            log.info("ActionsSettings already has custom values, skipping migration");
        }
    }

    private void migrateUISettings(OllamAssistSettings.State legacyState) {
        OllamAssistUISettings uiSettings = OllamAssistUISettings.getInstance();
        OllamAssistUISettings.State currentState = uiSettings.getState();

        if (currentState == null) {
            log.warn("OllamAssistUISettings state is null, cannot migrate");
            return;
        }

        // Only migrate if the new settings are still at default values
        if (isDefaultUISettings(currentState)) {
            log.info("Migrating UI settings");
            uiSettings.setContextPanelCollapsed(legacyState.uistate);
            log.info("UI settings migrated successfully");
        } else {
            log.info("OllamAssistUISettings already has custom values, skipping migration");
        }
    }

    private boolean isDefaultRAGSettings(RAGSettings.State state) {
        return "src/".equals(state.sources)
                && state.indexationSize == 5000
                && !state.webSearchEnabled
                && !state.ragEnabled;
    }

    private boolean isDefaultActionsSettings(ActionsSettings.State state) {
        return !state.isAutoApproveFileCreation();
    }

    private boolean isDefaultUISettings(OllamAssistUISettings.State state) {
        return !state.contextPanelCollapsed;
    }

    private void markMigrationCompleted() {
        myState.migrationCompleted = true;
        log.debug("Migration marked as completed");
    }

    public static class State {
        public boolean migrationCompleted = false;
    }
}