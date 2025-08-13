package fr.baretto.ollamassist.completion;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class LightModelService {
    private final SuggestionManager suggestionManager;

    public LightModelService(SuggestionManager suggestionManager) {
        this.suggestionManager = suggestionManager;
    }

    public void handleSuggestion(Editor editor) {
        ApplicationManager.getApplication().invokeLater(() -> {
            Document document = editor.getDocument();
            SelectionModel selectionModel = editor.getSelectionModel();

            String context;
            int caretOffset = editor.getCaretModel().getOffset();

            if (selectionModel.hasSelection()) {
                context = selectionModel.getSelectedText();
            } else {
                int startOffset = Math.max(0, caretOffset - 200);
                int endOffset = Math.min(document.getTextLength(), caretOffset);

                context = document.getText().substring(startOffset, endOffset);
            }
            new Task.Backgroundable(editor.getProject(), "Ollamassist prepare suggestion  ...", true) {

                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    String lineStartContent = getLineStartContent(editor).trim();
                    final String suggestion = extractCode(LightModelAssistant.get().complete(context, getFileExtension(editor)), lineStartContent);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        suggestionManager.showSuggestion(editor, editor.getCaretModel().getOffset(), suggestion);
                        attachKeyListener(editor, editor.getCaretModel().getOffset());
                    });
                }
            }.queue();
        });
    }


    public String getFileExtension(Editor editor) {
        try {
            FileEditorManager fileEditorManager = FileEditorManager.getInstance(editor.getProject());
            VirtualFile file = fileEditorManager.getSelectedFiles().length > 0 ? fileEditorManager.getSelectedFiles()[0] : null;

            if (file != null) {
                return file.getExtension();
            }
            return "";
        } catch (Exception e) {
            return "";
        }

    }

    public String extractCode(String code, String snippet) {
        if (code.contains("```")) {
            code = removeAfterSecondBackticks(code);
            code = code.replaceAll("```\\w+\\s*", "")
                    .replace("```", "")
                    .trim();
        }
        if (code.contains(snippet)) {
            return code.substring(code.indexOf(snippet) + snippet.length(), code.length() - 3);
        }
        return code;
    }

    public String removeAfterSecondBackticks(String input) {
        int firstIndex = input.indexOf("```");
        if (firstIndex != -1) {
            int secondIndex = input.indexOf("```", firstIndex + 3);
            if (secondIndex != -1) {
                return input.substring(0, secondIndex + 3);
            }
        }
        return input;
    }


    private String getLineStartContent(Editor editor) {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            Document document = editor.getDocument();

            int offset = editor.getCaretModel().getOffset();
            int lineStartOffset = document.getLineStartOffset(document.getLineNumber(offset));
            return document.getText().substring(lineStartOffset, offset);
        });
    }

    private void attachKeyListener(Editor editor, int offset) {
        editor.getContentComponent().addKeyListener(new SuggestionKeyListener(suggestionManager, editor));

        ApplicationManager.getApplication().invokeLater(() -> {
            EditorActionManager actionManager = EditorActionManager.getInstance();
            EditorActionHandler originalEnterHandler = actionManager.getActionHandler("EditorEnter");

            SuggestionEnterAction enterAction = new SuggestionEnterAction(suggestionManager, offset, originalEnterHandler);
            actionManager.setActionHandler("EditorEnter", enterAction.getHandler());
        });

    }

}