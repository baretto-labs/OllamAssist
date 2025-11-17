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
    private static final String ERROR_MESSAGE_FORMAT = "Error inserting suggestion: %s";

    private Inlay<?> currentInlay;
    private Inlay<?> loadingInlay;
    @Getter
    private String currentSuggestion;
    private LoadingInlayRenderer loadingRenderer;


    /**
     * Shows a loading indicator while the suggestion is being generated.
     */
    public void showLoading(Editor editor, int offset, String loadingMessage) {
        disposeCurrentInlay();
        disposeLoadingInlay();
        
        ApplicationManager.getApplication().invokeLater(() -> {
            InlayModel inlayModel = editor.getInlayModel();
            loadingRenderer = new LoadingInlayRenderer(editor, loadingMessage);
            loadingInlay = inlayModel.addInlineElement(offset, false, loadingRenderer);
        });
    }

    /**
     * Shows the actual suggestion, replacing any loading indicator.
     */
    public void showSuggestion(Editor editor, int offset, String suggestion) {
        disposeLoadingInlay();
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
                    log.error(String.format(ERROR_MESSAGE_FORMAT, e.getMessage()));
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
    
    /**
     * Disposes the loading indicator.
     */
    public void disposeLoadingInlay() {
        if (loadingInlay != null) {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (loadingInlay != null) {
                    loadingInlay.dispose();
                    loadingInlay = null;
                }
            });
        }
        
        if (loadingRenderer != null) {
            loadingRenderer.dispose();
            loadingRenderer = null;
        }
    }

    public boolean hasSuggestion() {
        return currentSuggestion != null;
    }

    public void clearSuggestion() {
        currentSuggestion = null;
        disposeCurrentInlay();
        disposeLoadingInlay();
    }

}