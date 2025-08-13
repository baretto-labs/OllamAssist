package fr.baretto.ollamassist.completion;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class SuggestionEnterAction {
    private final SuggestionManager suggestionManager;
    private final int offset;
    private final EditorActionHandler originalHandler;

    public SuggestionEnterAction(SuggestionManager suggestionManager, int offset, EditorActionHandler originalHandler) {
        this.suggestionManager = suggestionManager;
        this.offset = offset;
        this.originalHandler = originalHandler;
    }

    public EditorActionHandler getHandler() {
        return new EditorActionHandler() {
            @Override
            protected void doExecute(@NotNull Editor editor, Caret caret, DataContext dataContext) {
                if (suggestionManager.hasSuggestion()) {
                    insertSuggestion(editor);
                } else {
                    originalHandler.execute(editor, caret, dataContext);
                }
            }

            private void insertSuggestion(Editor editor) {
                ApplicationManager.getApplication().runWriteAction(() -> {
                    try {
                        editor.getDocument().insertString(offset, suggestionManager.getCurrentSuggestion());
                        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(editor.getProject());
                        psiDocumentManager.commitDocument(editor.getDocument());
                        CodeStyleManager.getInstance(editor.getProject()).reformatText(
                                psiDocumentManager.getPsiFile(editor.getDocument()), offset, offset + suggestionManager.getCurrentSuggestion().length()
                        );
                        editor.getCaretModel().moveToOffset(offset + suggestionManager.getCurrentSuggestion().length());
                    } catch (Exception e) {
                        log.error("Error during autocomplete");
                    } finally {
                        suggestionManager.clearSuggestion();
                    }
                });
            }
        };
    }
}