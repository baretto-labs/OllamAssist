package fr.baretto.ollamassist.completion;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.CaretModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MultiSuggestionManager.
 * Tests suggestion management, navigation, and state handling.
 */
class MultiSuggestionManagerTest {

    @Mock
    private Editor mockEditor;
    
    @Mock
    private CaretModel mockCaretModel;

    private MultiSuggestionManager manager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        manager = new MultiSuggestionManager();
        
        // Setup mock behavior
        when(mockEditor.getCaretModel()).thenReturn(mockCaretModel);
        when(mockCaretModel.getOffset()).thenReturn(100);
    }

    @Test
    void testInitialState() {
        assertFalse(manager.hasSuggestions(), "Should have no suggestions initially");
        assertFalse(manager.hasMultipleSuggestions(), "Should have no multiple suggestions initially");
        assertEquals(0, manager.getTotalSuggestions(), "Should have 0 total suggestions");
        assertEquals(0, manager.getCurrentSuggestionNumber(), "Should have 0 current suggestion number");
        assertNull(manager.getCurrentSuggestion(), "Current suggestion should be null");
    }

    @Test
    void testSingleSuggestion() {
        String suggestion = "System.out.println(\"Hello\");";
        manager.showSuggestion(mockEditor, 100, suggestion);

        assertTrue(manager.hasSuggestions(), "Should have suggestions");
        assertFalse(manager.hasMultipleSuggestions(), "Should not have multiple suggestions");
        assertEquals(1, manager.getTotalSuggestions(), "Should have 1 total suggestion");
        assertEquals(1, manager.getCurrentSuggestionNumber(), "Should be on suggestion 1");
        assertEquals(suggestion, manager.getCurrentSuggestion(), "Should return correct suggestion");
    }

    @Test
    void testMultipleSuggestions() {
        List<String> suggestions = List.of(
            "System.out.println(\"Hello\");",
            "System.out.println(\"World\");",
            "System.out.println(\"!\");"
        );
        
        manager.showSuggestions(mockEditor, 100, suggestions);

        assertTrue(manager.hasSuggestions(), "Should have suggestions");
        assertTrue(manager.hasMultipleSuggestions(), "Should have multiple suggestions");
        assertEquals(3, manager.getTotalSuggestions(), "Should have 3 total suggestions");
        assertEquals(1, manager.getCurrentSuggestionNumber(), "Should start on suggestion 1");
        assertEquals(suggestions.get(0), manager.getCurrentSuggestion(), "Should return first suggestion");
    }

    @Test
    void testNavigationNextSuggestion() {
        List<String> suggestions = List.of("suggestion1", "suggestion2", "suggestion3");
        manager.showSuggestions(mockEditor, 100, suggestions);

        assertEquals(1, manager.getCurrentSuggestionNumber(), "Should start on suggestion 1");
        assertEquals("suggestion1", manager.getCurrentSuggestion());

        // Navigate to next
        assertTrue(manager.nextSuggestion(mockEditor), "Should successfully navigate to next");
        assertEquals(2, manager.getCurrentSuggestionNumber(), "Should be on suggestion 2");
        assertEquals("suggestion2", manager.getCurrentSuggestion());

        // Navigate to next again
        assertTrue(manager.nextSuggestion(mockEditor), "Should successfully navigate to next");
        assertEquals(3, manager.getCurrentSuggestionNumber(), "Should be on suggestion 3");
        assertEquals("suggestion3", manager.getCurrentSuggestion());

        // Navigate to next (should wrap around)
        assertTrue(manager.nextSuggestion(mockEditor), "Should successfully wrap around");
        assertEquals(1, manager.getCurrentSuggestionNumber(), "Should wrap around to suggestion 1");
        assertEquals("suggestion1", manager.getCurrentSuggestion());
    }

    @Test
    void testNavigationPreviousSuggestion() {
        List<String> suggestions = List.of("suggestion1", "suggestion2", "suggestion3");
        manager.showSuggestions(mockEditor, 100, suggestions);

        assertEquals(1, manager.getCurrentSuggestionNumber(), "Should start on suggestion 1");

        // Navigate to previous (should wrap around to last)
        assertTrue(manager.previousSuggestion(mockEditor), "Should successfully navigate to previous");
        assertEquals(3, manager.getCurrentSuggestionNumber(), "Should wrap around to suggestion 3");
        assertEquals("suggestion3", manager.getCurrentSuggestion());

        // Navigate to previous
        assertTrue(manager.previousSuggestion(mockEditor), "Should successfully navigate to previous");
        assertEquals(2, manager.getCurrentSuggestionNumber(), "Should be on suggestion 2");
        assertEquals("suggestion2", manager.getCurrentSuggestion());

        // Navigate to previous again
        assertTrue(manager.previousSuggestion(mockEditor), "Should successfully navigate to previous");
        assertEquals(1, manager.getCurrentSuggestionNumber(), "Should be on suggestion 1");
        assertEquals("suggestion1", manager.getCurrentSuggestion());
    }

    @Test
    void testNavigationWithSingleSuggestion() {
        manager.showSuggestion(mockEditor, 100, "single suggestion");

        // Navigation should not work with single suggestion
        assertFalse(manager.nextSuggestion(mockEditor), "Should not navigate with single suggestion");
        assertFalse(manager.previousSuggestion(mockEditor), "Should not navigate with single suggestion");
        assertEquals(1, manager.getCurrentSuggestionNumber(), "Should stay on suggestion 1");
    }

    @Test
    void testNavigationWithNoSuggestions() {
        // Navigation should not work with no suggestions
        assertFalse(manager.nextSuggestion(mockEditor), "Should not navigate with no suggestions");
        assertFalse(manager.previousSuggestion(mockEditor), "Should not navigate with no suggestions");
        assertEquals(0, manager.getCurrentSuggestionNumber(), "Should have 0 current suggestion");
    }

    @Test
    void testClearSuggestions() {
        List<String> suggestions = List.of("suggestion1", "suggestion2");
        manager.showSuggestions(mockEditor, 100, suggestions);

        assertTrue(manager.hasSuggestions(), "Should have suggestions");
        assertEquals(2, manager.getTotalSuggestions(), "Should have 2 suggestions");

        manager.clearSuggestions();

        assertFalse(manager.hasSuggestions(), "Should have no suggestions after clear");
        assertFalse(manager.hasMultipleSuggestions(), "Should have no multiple suggestions after clear");
        assertEquals(0, manager.getTotalSuggestions(), "Should have 0 total suggestions after clear");
        assertEquals(0, manager.getCurrentSuggestionNumber(), "Should have 0 current suggestion after clear");
        assertNull(manager.getCurrentSuggestion(), "Current suggestion should be null after clear");
    }

    @Test
    void testEmptySuggestionList() {
        List<String> emptySuggestions = new ArrayList<>();
        manager.showSuggestions(mockEditor, 100, emptySuggestions);

        assertFalse(manager.hasSuggestions(), "Should have no suggestions with empty list");
        assertEquals(0, manager.getTotalSuggestions(), "Should have 0 total suggestions");
        assertNull(manager.getCurrentSuggestion(), "Current suggestion should be null with empty list");
    }

    @Test
    void testInsertCurrentSuggestion_NoSuggestions() {
        // Should handle gracefully when no suggestions exist
        assertDoesNotThrow(() -> manager.insertCurrentSuggestion(mockEditor), 
            "Should handle insertion with no suggestions gracefully");
    }

    @Test
    void testDebugInfo() {
        String debugInfo = manager.getDebugInfo();
        assertNotNull(debugInfo, "Debug info should not be null");
        assertTrue(debugInfo.contains("suggestions=0"), "Debug info should show 0 suggestions initially");
        assertTrue(debugInfo.contains("current=0"), "Debug info should show current index 0 initially");
        assertTrue(debugInfo.contains("hasMultiple=false"), "Debug info should show hasMultiple=false initially");

        // Add suggestions and test debug info again
        manager.showSuggestion(mockEditor, 100, "test suggestion");
        String debugInfoWithSuggestion = manager.getDebugInfo();
        assertTrue(debugInfoWithSuggestion.contains("suggestions=1"), "Debug info should show 1 suggestion");
    }

    @Test
    void testMultilineSuggestion() {
        String multilineSuggestion = "if (condition) {\n    doSomething();\n    doSomethingElse();\n}";
        manager.showSuggestion(mockEditor, 100, multilineSuggestion);

        assertTrue(manager.hasSuggestions(), "Should have suggestions");
        assertEquals(multilineSuggestion, manager.getCurrentSuggestion(), "Should handle multiline suggestions");
    }

    @Test
    void testSuggestionReplacement() {
        // Show initial suggestions
        List<String> initialSuggestions = List.of("suggestion1", "suggestion2");
        manager.showSuggestions(mockEditor, 100, initialSuggestions);
        assertEquals(2, manager.getTotalSuggestions(), "Should have 2 initial suggestions");

        // Replace with new suggestions
        List<String> newSuggestions = List.of("newSuggestion1", "newSuggestion2", "newSuggestion3");
        manager.showSuggestions(mockEditor, 100, newSuggestions);
        assertEquals(3, manager.getTotalSuggestions(), "Should have 3 new suggestions");
        assertEquals(1, manager.getCurrentSuggestionNumber(), "Should reset to first suggestion");
        assertEquals("newSuggestion1", manager.getCurrentSuggestion(), "Should show first new suggestion");
    }

    @Test
    void testLargeSuggestionList() {
        // Test with a large number of suggestions
        List<String> largeSuggestionList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            largeSuggestionList.add("suggestion" + i);
        }

        manager.showSuggestions(mockEditor, 100, largeSuggestionList);
        
        assertTrue(manager.hasSuggestions(), "Should have suggestions");
        assertTrue(manager.hasMultipleSuggestions(), "Should have multiple suggestions");
        assertEquals(100, manager.getTotalSuggestions(), "Should have 100 suggestions");
        assertEquals(1, manager.getCurrentSuggestionNumber(), "Should start on first suggestion");

        // Navigate to last suggestion
        for (int i = 0; i < 99; i++) {
            assertTrue(manager.nextSuggestion(mockEditor), "Should navigate successfully");
        }
        assertEquals(100, manager.getCurrentSuggestionNumber(), "Should be on last suggestion");
        assertEquals("suggestion99", manager.getCurrentSuggestion(), "Should show last suggestion");

        // Wrap around to first
        assertTrue(manager.nextSuggestion(mockEditor), "Should wrap around");
        assertEquals(1, manager.getCurrentSuggestionNumber(), "Should wrap to first suggestion");
        assertEquals("suggestion0", manager.getCurrentSuggestion(), "Should show first suggestion");
    }
}