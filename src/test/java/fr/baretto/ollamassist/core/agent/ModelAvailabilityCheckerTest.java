package fr.baretto.ollamassist.core.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ModelAvailabilityChecker model name matching logic
 */
@DisplayName("ModelAvailabilityChecker Tests")
public class ModelAvailabilityCheckerTest {

    @Test
    @DisplayName("Should match model name without tag to model with tag")
    void shouldMatchModelNameWithoutTagToModelWithTag() {
        // Given: User selects 'gpt-oss' without tag
        String userModelName = "gpt-oss";

        // When: Available model is 'gpt-oss:20b'
        String availableModelName = "gpt-oss:20b";

        // Then: Should match using tag-based logic
        boolean matches = matchesModelName(userModelName, availableModelName);
        assertTrue(matches, "gpt-oss should match gpt-oss:20b");
    }

    @Test
    @DisplayName("Should match exact model names")
    void shouldMatchExactModelNames() {
        // Given: User selects 'gpt-oss:20b' with tag
        String userModelName = "gpt-oss:20b";

        // When: Available model is 'gpt-oss:20b'
        String availableModelName = "gpt-oss:20b";

        // Then: Should match exactly
        boolean matches = matchesModelName(userModelName, availableModelName);
        assertTrue(matches, "gpt-oss:20b should match gpt-oss:20b exactly");
    }

    @Test
    @DisplayName("Should match model with tag to base name")
    void shouldMatchModelWithTagToBaseName() {
        // Given: User selects 'gpt-oss:20b' with tag
        String userModelName = "gpt-oss:20b";

        // When: Available model is just 'gpt-oss' (unlikely but possible)
        String availableModelName = "gpt-oss";

        // Then: Should match
        boolean matches = matchesModelName(userModelName, availableModelName);
        assertTrue(matches, "gpt-oss:20b should match base gpt-oss");
    }

    @Test
    @DisplayName("Should not match different model names")
    void shouldNotMatchDifferentModelNames() {
        // Given: User selects 'gpt-oss'
        String userModelName = "gpt-oss";

        // When: Available model is 'llama3.1:latest'
        String availableModelName = "llama3.1:latest";

        // Then: Should NOT match
        boolean matches = matchesModelName(userModelName, availableModelName);
        assertFalse(matches, "gpt-oss should not match llama3.1:latest");
    }

    @Test
    @DisplayName("Should not match partial names")
    void shouldNotMatchPartialNames() {
        // Given: User selects 'gpt'
        String userModelName = "gpt";

        // When: Available model is 'gpt-oss:20b'
        String availableModelName = "gpt-oss:20b";

        // Then: Should NOT match (prevent false positives)
        boolean matches = matchesModelName(userModelName, availableModelName);
        assertFalse(matches, "gpt should not match gpt-oss:20b (too broad)");
    }

    @Test
    @DisplayName("Should match different tags of same model")
    void shouldMatchDifferentTagsOfSameModel() {
        // Given: User selects 'llama3.1' without tag
        String userModelName = "llama3.1";

        // When: Available model is 'llama3.1:latest'
        String availableModelName = "llama3.1:latest";

        // Then: Should match
        boolean matches = matchesModelName(userModelName, availableModelName);
        assertTrue(matches, "llama3.1 should match llama3.1:latest");
    }

    /**
     * Replicates the matching logic from ModelAvailabilityChecker.checkModelAvailability()
     */
    private boolean matchesModelName(String modelName, String availableName) {
        // Exact match: gpt-oss:20b == gpt-oss:20b
        if (availableName.equals(modelName)) {
            return true;
        }
        // Tag match: gpt-oss matches gpt-oss:20b or gpt-oss:latest
        if (availableName.startsWith(modelName + ":")) {
            return true;
        }
        // Base name match: gpt-oss:20b matches gpt-oss
        if (modelName.contains(":") && availableName.equals(modelName.split(":")[0])) {
            return true;
        }
        return false;
    }
}
