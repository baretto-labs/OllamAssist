package fr.baretto.ollamassist.git;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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