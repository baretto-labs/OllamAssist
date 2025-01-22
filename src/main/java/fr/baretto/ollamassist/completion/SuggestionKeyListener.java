package fr.baretto.ollamassist.completion;

import com.intellij.openapi.editor.Editor;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class SuggestionKeyListener extends KeyAdapter {
    private final SuggestionManager suggestionManager;
    private final Editor editor;

    public SuggestionKeyListener(SuggestionManager suggestionManager, Editor editor) {
        this.suggestionManager = suggestionManager;
        this.editor = editor;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            suggestionManager.insertSuggestion(editor);
        } else {
            suggestionManager.disposeCurrentInlay();
        }
    }
}
