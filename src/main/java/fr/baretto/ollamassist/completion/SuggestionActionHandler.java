package fr.baretto.ollamassist.completion;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Action handler for managing suggestion acceptance and dismissal.
 * Uses IntelliJ's EditorActionHandler system for better integration.
 */
@Slf4j
public class SuggestionActionHandler extends EditorActionHandler {
    
    private final MultiSuggestionManager suggestionManager;
    private final EditorActionHandler originalHandler;
    
    public SuggestionActionHandler(@NotNull MultiSuggestionManager suggestionManager, 
                                 @Nullable EditorActionHandler originalHandler) {
        this.suggestionManager = suggestionManager;
        this.originalHandler = originalHandler;
    }
    
    @Override
    protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
        log.debug("SuggestionActionHandler.doExecute() called");
        
        // Check if we have active suggestions
        if (suggestionManager.hasSuggestions()) {
            log.debug("Has suggestions - inserting current suggestion");
            suggestionManager.insertCurrentSuggestion(editor);
            return;
        }
        
        log.debug("No suggestions - delegating to original handler");
        // No suggestions active, delegate to original handler
        if (originalHandler != null) {
            originalHandler.execute(editor, caret, dataContext);
        }
    }
    
    @Override
    protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
        // Always enabled - let doExecute decide what to do
        return true;
    }
}