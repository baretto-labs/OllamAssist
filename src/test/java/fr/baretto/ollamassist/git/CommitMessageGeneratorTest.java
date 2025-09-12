package fr.baretto.ollamassist.git;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for CommitMessageGenerator functionality.
 * Tests basic instantiation and reflection mechanisms.
 */
class CommitMessageGeneratorTest {

    @Test
    void should_instantiate_generator_without_error() {
        // When
        CommitMessageGenerator generator = new CommitMessageGenerator();
        
        // Then
        assertNotNull(generator);
    }

    @Test
    void should_handle_null_source_in_reflection() {
        // Given
        CommitMessageGenerator generator = new CommitMessageGenerator();
        
        // When: Try reflection with null source
        var result = generator.tryGetChangesViaReflection(null, "null-source");
        
        // Then: Should return empty result without throwing
        assertNotNull(result);
        assertFalse(result.hasSelection());
    }

    @Test
    void should_handle_object_without_relevant_methods() {
        // Given: Object with no relevant methods
        Object irrelevantObject = new Object();
        CommitMessageGenerator generator = new CommitMessageGenerator();
        
        // When: Try reflection
        var result = generator.tryGetChangesViaReflection(irrelevantObject, "irrelevant");
        
        // Then: Should return empty
        assertNotNull(result);
        assertFalse(result.hasSelection());
    }

    @Test
    void should_detect_selection_via_reflection() {
        // Given: Object with mock selection methods
        TestSelectionPanel panel = new TestSelectionPanel();
        CommitMessageGenerator generator = new CommitMessageGenerator();
        
        // Debug: Verify our panel methods work directly
        List<Change> directChanges = panel.getSelectedChanges();
        assertEquals(1, directChanges.size(), "Panel should return 1 change directly");
        assertNotNull(directChanges.get(0), "The change should not be null");
        
        // When: Use reflection
        var result = generator.tryGetChangesViaReflection(panel, "test-panel");
        
        // Then: Should detect selection
        assertNotNull(result);
        assertTrue(result.hasSelection(), 
            "Should detect selection - found " + result.changes().size() + " changes");
        assertEquals(1, result.changes().size());
    }

    @Test
    void should_handle_empty_collections() {
        // Given: Panel with empty collections
        TestEmptyPanel emptyPanel = new TestEmptyPanel();
        CommitMessageGenerator generator = new CommitMessageGenerator();
        
        // When: Use reflection
        var result = generator.tryGetChangesViaReflection(emptyPanel, "empty-panel");
        
        // Then: Should return no selection
        assertNotNull(result);
        assertFalse(result.hasSelection());
    }

    @Test
    void should_handle_method_exceptions() {
        // Given: Object that throws exceptions
        Object problematicPanel = new Object() {
            @SuppressWarnings("unused")
            public List<Change> getSelectedChanges() {
                throw new RuntimeException("Method error");
            }
        };
        
        CommitMessageGenerator generator = new CommitMessageGenerator();
        
        // When: Try reflection (should not throw)
        assertDoesNotThrow(() -> {
            var result = generator.tryGetChangesViaReflection(problematicPanel, "problematic");
            assertFalse(result.hasSelection());
        });
    }

    // Helper test classes
    static class TestSelectionPanel {
        @SuppressWarnings("unused") // Called via reflection
        public List<Change> getSelectedChanges() {
            return Arrays.asList(createMockChange());
        }
        
        @SuppressWarnings("unused") // Called via reflection
        public List<FilePath> getUnversionedFiles() {
            return Collections.emptyList();
        }
    }

    static class TestEmptyPanel {
        @SuppressWarnings("unused") // Called via reflection
        public List<Change> getSelectedChanges() {
            return Collections.emptyList();
        }
        
        @SuppressWarnings("unused") // Called via reflection
        public List<FilePath> getUnversionedFiles() {
            return Collections.emptyList();
        }
    }

    // Helper method to create a mock Change without using the problematic constructor
    private static Change createMockChange() {
        // Create a mock ContentRevision to satisfy the Change constructor
        com.intellij.openapi.vcs.changes.ContentRevision mockRevision = 
            new com.intellij.openapi.vcs.changes.ContentRevision() {
                @Override
                public String getContent() {
                    return "test content";
                }

                @Override
                public com.intellij.openapi.vcs.FilePath getFile() {
                    return null; // This is acceptable for our test
                }

                @Override
                public com.intellij.openapi.vcs.history.VcsRevisionNumber getRevisionNumber() {
                    return null;
                }
            };
        
        return new Change(null, mockRevision) {
            @Override
            public com.intellij.openapi.vcs.changes.ContentRevision getBeforeRevision() {
                return null;
            }

            @Override
            public com.intellij.openapi.vcs.changes.ContentRevision getAfterRevision() {
                return mockRevision;
            }
        };
    }
}