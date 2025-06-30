package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;

/**
 * Provides a focused window of code around the user's caret position.
 * Extracts a substring centered on the caret, with a configurable character window size.
 */
public class FocusedWindowContextProvider implements FocusContextProvider {

    private static final int WINDOW_SIZE = 8000;
    private final Project project;

    public FocusedWindowContextProvider(Project project) {
        this.project = project;
    }

    @Override
    public Content get(Query query) {
        try {
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor == null) {
                return null;
            }

            Document document = editor.getDocument();
            VirtualFile file = FileEditorManager.getInstance(project).getSelectedFiles()[0];
            if (file == null) {
                return null;
            }

            String fullText = document.getText();
            int caretOffset = editor.getCaretModel().getOffset();

            int halfWindow = WINDOW_SIZE / 2;
            int start = Math.max(0, caretOffset - halfWindow);
            int end = Math.min(fullText.length(), caretOffset + halfWindow);

            String focusedText = fullText.substring(start, end);

            return Content.from(focusedText);
        } catch (Exception e) {
            return Content.from("");
        }
    }
}