package fr.baretto.ollamassist.completion;

import com.intellij.openapi.editor.Editor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * Enhanced key listener for multi-suggestion navigation and interaction.
 * Handles Tab/Shift+Tab for navigation, Enter for acceptance, and Escape for dismissal.
 */
@Slf4j
public class EnhancedSuggestionKeyListener implements KeyListener {

    private final MultiSuggestionManager suggestionManager;
    private final Editor editor;

    public EnhancedSuggestionKeyListener(@NotNull MultiSuggestionManager suggestionManager,
                                         @NotNull Editor editor) {
        this.suggestionManager = suggestionManager;
        this.editor = editor;
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Handle regular typing - dismiss suggestions on any character input
        if (!isNavigationKey(e) && !isControlKey(e) && suggestionManager.hasSuggestions()) {
            log.debug("Dismissing suggestions due to character input: '{}'", e.getKeyChar());
            suggestionManager.clearSuggestions();
            removeKeyListener();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (!suggestionManager.hasSuggestions()) {
            return;
        }

        boolean handled = false;

        switch (e.getKeyCode()) {
            case KeyEvent.VK_TAB:
                if (e.isShiftDown()) {
                    // Shift+Tab: Previous suggestion
                    handled = suggestionManager.previousSuggestion(editor);
                } else {
                    // Tab: Next suggestion
                    handled = suggestionManager.nextSuggestion(editor);
                }
                break;

            case KeyEvent.VK_ENTER:
                // Enter: Accept current suggestion
                log.debug("Enter key pressed! About to call insertCurrentSuggestion()");
                suggestionManager.insertCurrentSuggestion(editor);
                log.debug("insertCurrentSuggestion() called, now removing key listener");
                removeKeyListener();
                handled = true;
                log.debug("Accepted suggestion via Enter key");
                break;

            case KeyEvent.VK_ESCAPE:
                // Escape: Dismiss suggestions
                suggestionManager.clearSuggestions();
                removeKeyListener();
                handled = true;
                log.debug("Dismissed suggestions via Escape key");
                break;

            case KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT, KeyEvent.VK_DOWN, KeyEvent.VK_UP:
                // Arrow keys: Dismiss suggestions (cursor movement)
                suggestionManager.clearSuggestions();
                removeKeyListener();
                log.debug("Dismissed suggestions due to arrow key navigation");
                break;
            case KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_PAGE_UP, KeyEvent.VK_END, KeyEvent.VK_HOME:
                // Navigation keys: Dismiss suggestions
                suggestionManager.clearSuggestions();
                removeKeyListener();
                log.debug("Dismissed suggestions due to navigation key");
                break;
            case KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE:
                // Editing keys: Dismiss suggestions
                suggestionManager.clearSuggestions();
                removeKeyListener();
                log.debug("Dismissed suggestions due to editing action");
                break;
            default:
                log.debug("Do nothing suggestions due to editing action");
        }

        // Consume the event if we handled it
        if (handled) {
            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // No action needed on key release
    }

    /**
     * Checks if the key event is a navigation key that we handle.
     */
    private boolean isNavigationKey(@NotNull KeyEvent e) {
        return e.getKeyCode() == KeyEvent.VK_TAB ||
                e.getKeyCode() == KeyEvent.VK_ENTER ||
                e.getKeyCode() == KeyEvent.VK_ESCAPE ||
                e.getKeyCode() == KeyEvent.VK_UP ||
                e.getKeyCode() == KeyEvent.VK_DOWN ||
                e.getKeyCode() == KeyEvent.VK_LEFT ||
                e.getKeyCode() == KeyEvent.VK_RIGHT;
    }

    /**
     * Checks if the key event is a control key (Ctrl, Alt, Shift, etc.).
     */
    private boolean isControlKey(@NotNull KeyEvent e) {
        return e.isControlDown() ||
                e.isAltDown() ||
                e.isMetaDown() ||
                e.getKeyCode() == KeyEvent.VK_CONTROL ||
                e.getKeyCode() == KeyEvent.VK_ALT ||
                e.getKeyCode() == KeyEvent.VK_META ||
                e.getKeyCode() == KeyEvent.VK_SHIFT;
    }

    /**
     * Removes this key listener from the editor.
     */
    private void removeKeyListener() {
        try {
            editor.getContentComponent().removeKeyListener(this);
            log.debug("Removed enhanced suggestion key listener");
        } catch (Exception e) {
            log.warn("Failed to remove key listener", e);
        }
    }
}