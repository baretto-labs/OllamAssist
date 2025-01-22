package fr.baretto.ollamassist.completion;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
public class SuggestionManager {
    private Inlay<?> currentInlay;
    @Getter
    private String currentSuggestion;


    public void showSuggestion(Editor editor, int offset, String suggestion) {
        disposeCurrentInlay();
        ApplicationManager.getApplication().invokeLater(() -> {
            InlayModel inlayModel = editor.getInlayModel();
            currentInlay = inlayModel.addBlockElement(offset, true, false, 0, new InlayRenderer(Arrays.stream(suggestion.split("\n")).toList(), editor));
            currentSuggestion = suggestion;
        });
    }

    public void insertSuggestion(Editor editor) {
        if (currentInlay != null && currentSuggestion != null) {
            ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(editor.getProject(), () -> {
                try {
                    Document document = editor.getDocument();
                    CaretModel caretModel = editor.getCaretModel();
                    int caretOffset = caretModel.getOffset();

                    document.insertString(caretOffset, currentSuggestion);

                    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(editor.getProject());
                    psiDocumentManager.commitDocument(document);
                    CodeStyleManager.getInstance(editor.getProject()).reformatText(
                            psiDocumentManager.getPsiFile(document), caretOffset, caretOffset + currentSuggestion.length()
                    );

                    caretModel.moveToOffset(caretOffset + currentSuggestion.length());

                    disposeCurrentInlay();
                    currentSuggestion = null;
                } catch (Exception e) {
                    log.error("Error inserting suggestion: " + e.getMessage());
                }
            }, "Insert Suggestion", null));
        }
    }

    public void disposeCurrentInlay() {
        if (currentInlay != null) {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (currentInlay != null) {
                    currentInlay.dispose();
                    currentInlay = null;
                    currentSuggestion = null;
                }
            });
        }
    }

    public boolean hasSuggestion() {
        return currentSuggestion != null;
    }

    public void clearSuggestion() {
        currentSuggestion = null;
        disposeCurrentInlay();
    }

}