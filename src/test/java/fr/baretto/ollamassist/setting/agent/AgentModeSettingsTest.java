package fr.baretto.ollamassist.setting.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentModeSettings Tests")
class AgentModeSettingsTest {

    private AgentModeSettings settings;

    @BeforeEach
    void setUp() {
        settings = new AgentModeSettings();
    }

    @Test
    @DisplayName("Should have correct default values")
    void shouldHaveCorrectDefaultValues() {
        // When & Then
        assertThat(settings.isAgentModeEnabled()).isTrue(); // Activé par défaut maintenant
        assertThat(settings.isAutoTaskApprovalEnabled()).isFalse();
        assertThat(settings.getMaxTasksPerSession()).isEqualTo(10);
        assertThat(settings.isSnapshotEnabled()).isTrue();
        assertThat(settings.isMcpIntegrationEnabled()).isTrue();
        assertThat(settings.getSecurityLevel()).isEqualTo(AgentModeSettings.AgentSecurityLevel.STANDARD);
        assertThat(settings.isTaskProgressUIEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should enable/disable agent mode")
    void shouldEnableDisableAgentMode() {
        // When
        settings.setAgentModeEnabled(true);

        // Then
        assertThat(settings.isAgentModeEnabled()).isTrue();

        // When
        settings.setAgentModeEnabled(false);

        // Then
        assertThat(settings.isAgentModeEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should configure auto task approval")
    void shouldConfigureAutoTaskApproval() {
        // When
        settings.setAutoTaskApprovalEnabled(true);

        // Then
        assertThat(settings.isAutoTaskApprovalEnabled()).isTrue();

        // When
        settings.setAutoTaskApprovalEnabled(false);

        // Then
        assertThat(settings.isAutoTaskApprovalEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should validate max tasks per session bounds")
    void shouldValidateMaxTasksPerSessionBounds() {
        // When - Set normal value
        settings.setMaxTasksPerSession(50);

        // Then
        assertThat(settings.getMaxTasksPerSession()).isEqualTo(50);

        // When - Set value below minimum
        settings.setMaxTasksPerSession(0);

        // Then - Should be clamped to minimum
        assertThat(settings.getMaxTasksPerSession()).isEqualTo(1);

        // When - Set value above maximum
        settings.setMaxTasksPerSession(150);

        // Then - Should be clamped to maximum
        assertThat(settings.getMaxTasksPerSession()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should configure security level")
    void shouldConfigureSecurityLevel() {
        // When
        settings.setSecurityLevel(AgentModeSettings.AgentSecurityLevel.STRICT);

        // Then
        assertThat(settings.getSecurityLevel()).isEqualTo(AgentModeSettings.AgentSecurityLevel.STRICT);

        // When
        settings.setSecurityLevel(AgentModeSettings.AgentSecurityLevel.EXPERT);

        // Then
        assertThat(settings.getSecurityLevel()).isEqualTo(AgentModeSettings.AgentSecurityLevel.EXPERT);
    }

    @Test
    @DisplayName("Should check approval requirements based on security level and auto-approval")
    void shouldCheckApprovalRequirementsBasedOnSecurityLevelAndAutoApproval() {
        // Given - Auto approval enabled
        settings.setAutoTaskApprovalEnabled(true);

        // When & Then - Auto approval should bypass all security checks
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.READ_FILE)).isFalse();
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.WRITE_FILE)).isFalse();
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.DELETE_FILE)).isFalse();
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.EXECUTE_COMMAND)).isFalse();

        // Given - Auto approval disabled, STRICT security level
        settings.setAutoTaskApprovalEnabled(false);
        settings.setSecurityLevel(AgentModeSettings.AgentSecurityLevel.STRICT);

        // When & Then - All actions should require approval
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.READ_FILE)).isTrue();
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.WRITE_FILE)).isTrue();
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.DELETE_FILE)).isTrue();
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.EXECUTE_COMMAND)).isTrue();

        // Given - STANDARD security level
        settings.setSecurityLevel(AgentModeSettings.AgentSecurityLevel.STANDARD);

        // When & Then - Only risky actions should require approval
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.READ_FILE)).isFalse();
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.WRITE_FILE)).isTrue();
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.DELETE_FILE)).isTrue();
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.EXECUTE_COMMAND)).isTrue();

        // Given - EXPERT security level
        settings.setSecurityLevel(AgentModeSettings.AgentSecurityLevel.EXPERT);

        // When & Then - Only high-risk actions should require approval
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.READ_FILE)).isFalse();
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.WRITE_FILE)).isFalse();
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.DELETE_FILE)).isTrue();
        assertThat(settings.requiresApproval(AgentModeSettings.AgentActionType.EXECUTE_COMMAND)).isTrue();
    }

    @Test
    @DisplayName("Should validate configuration")
    void shouldValidateConfiguration() {
        // When - Valid configuration
        settings.setMaxTasksPerSession(10);
        settings.setSecurityLevel(AgentModeSettings.AgentSecurityLevel.STANDARD);

        // Then
        assertThat(settings.isConfigurationValid()).isTrue();

        // When - Invalid max tasks (out of range)
        settings.setMaxTasksPerSession(0);

        // Then - Should still be valid because setter clamps the value
        assertThat(settings.isConfigurationValid()).isTrue();
        assertThat(settings.getMaxTasksPerSession()).isEqualTo(1);

        // When - Null security level (simulated)
        AgentModeSettings.State invalidState = new AgentModeSettings.State();
        invalidState.securityLevel = null;
        settings.loadState(invalidState);

        // Then
        assertThat(settings.isConfigurationValid()).isFalse();
    }

    @Test
    @DisplayName("Should generate configuration summary")
    void shouldGenerateConfigurationSummary() {
        // Given
        settings.setAgentModeEnabled(true);
        settings.setSecurityLevel(AgentModeSettings.AgentSecurityLevel.EXPERT);
        settings.setMaxTasksPerSession(15);
        settings.setAutoTaskApprovalEnabled(true);

        // When
        String summary = settings.getConfigurationSummary();

        // Then
        assertThat(summary).contains("Mode Agent: Activé")
                .contains("Sécurité: Expert - La plupart des actions sont automatiques")
                .contains("Max Tâches: 15")
                .contains("Auto-approbation: Oui");
    }

    @Test
    @DisplayName("Should reset to defaults")
    void shouldResetToDefaults() {
        // Given - Modified settings
        settings.setAgentModeEnabled(true);
        settings.setAutoTaskApprovalEnabled(true);
        settings.setMaxTasksPerSession(50);
        settings.setSecurityLevel(AgentModeSettings.AgentSecurityLevel.EXPERT);

        // When
        settings.resetToDefaults();

        // Then
        assertThat(settings.isAgentModeEnabled()).isTrue(); // Activé par défaut maintenant
        assertThat(settings.isAutoTaskApprovalEnabled()).isFalse();
        assertThat(settings.getMaxTasksPerSession()).isEqualTo(10);
        assertThat(settings.getSecurityLevel()).isEqualTo(AgentModeSettings.AgentSecurityLevel.STANDARD);
    }

    @Test
    @DisplayName("Should test action type risk levels")
    void shouldTestActionTypeRiskLevels() {
        // Safe actions
        assertThat(AgentModeSettings.AgentActionType.READ_FILE.isRisky()).isFalse();
        assertThat(AgentModeSettings.AgentActionType.READ_FILE.isHighRisk()).isFalse();
        assertThat(AgentModeSettings.AgentActionType.LIST_DIRECTORY.isRisky()).isFalse();
        assertThat(AgentModeSettings.AgentActionType.LIST_DIRECTORY.isHighRisk()).isFalse();

        // Risky but not high risk actions
        assertThat(AgentModeSettings.AgentActionType.WRITE_FILE.isRisky()).isTrue();
        assertThat(AgentModeSettings.AgentActionType.WRITE_FILE.isHighRisk()).isFalse();
        assertThat(AgentModeSettings.AgentActionType.CREATE_DIRECTORY.isRisky()).isTrue();
        assertThat(AgentModeSettings.AgentActionType.CREATE_DIRECTORY.isHighRisk()).isFalse();

        // High risk actions
        assertThat(AgentModeSettings.AgentActionType.DELETE_FILE.isRisky()).isTrue();
        assertThat(AgentModeSettings.AgentActionType.DELETE_FILE.isHighRisk()).isTrue();
        assertThat(AgentModeSettings.AgentActionType.EXECUTE_COMMAND.isRisky()).isTrue();
        assertThat(AgentModeSettings.AgentActionType.EXECUTE_COMMAND.isHighRisk()).isTrue();
    }

    @Test
    @DisplayName("Should test security level descriptions")
    void shouldTestSecurityLevelDescriptions() {
        // When & Then
        assertThat(AgentModeSettings.AgentSecurityLevel.STRICT.getDescription())
                .isEqualTo("Strict - Toutes les actions nécessitent une approbation");
        assertThat(AgentModeSettings.AgentSecurityLevel.STANDARD.getDescription())
                .isEqualTo("Standard - Actions sûres automatiques");
        assertThat(AgentModeSettings.AgentSecurityLevel.EXPERT.getDescription())
                .isEqualTo("Expert - La plupart des actions sont automatiques");

        assertThat(AgentModeSettings.AgentSecurityLevel.STRICT.toString())
                .hasToString("Strict - Toutes les actions nécessitent une approbation");
    }
}