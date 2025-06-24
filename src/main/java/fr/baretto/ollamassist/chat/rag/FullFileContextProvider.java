package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;

/**
 * - This class is part of the LangChain4J integration with IntelliJ IDEA, specifically designed for Retrieval-Augmented Generation (RAG) tasks.
 * - The implementation assumes that there is at most one selected file in the editor at any given time (getSelectedFiles()[0]).
 * - If no text editor or file is available, it gracefully returns null to indicate the absence of context.
 */
public class FullFileContextProvider implements FocusContextProvider {

    private final Project project;

    public FullFileContextProvider(Project project) {
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

            return Content.from(document.getText());
        } catch (Exception e) {
            return Content.from("");
        }
    }
}
