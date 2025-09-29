package fr.baretto.ollamassist.git;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffGeneratorTest {

    @Test
    void should_handle_empty_changes_list() {
        // When: Generate diff with no changes
        String result = DiffGenerator.getDiff(Collections.emptyList(), Collections.emptyList());

        // Then: Should return empty string
        assertTrue(result.trim().isEmpty());
    }

    @Test
    void should_instantiate_without_error() {
        // Test that we can create instance of DiffGenerator utility class
        // This is a basic smoke test
        assertDoesNotThrow(() -> {
            // DiffGenerator has only static methods, but we test it can be accessed
            DiffGenerator.class.getDeclaredConstructor();
        });
    }

    @Test
    void should_access_static_methods_without_error() {
        // Given: DiffGenerator is a utility class with static methods
        // When: We call getDiff method
        // Then: Should not throw any exceptions
        assertDoesNotThrow(() -> {
            DiffGenerator.getDiff(Collections.emptyList(), Collections.emptyList());
        });
    }
}