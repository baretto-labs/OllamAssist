package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.rag.content.Content;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides a focused window of code around the user's caret position.
 * Extracts a substring centered on the caret, with a configurable character window size.
 */
public class WorkspaceContextRetriever {

    private static final int WINDOW_SIZE = 5000;
    private static final long MAX_FILE_SIZE = 200L * 1024L;
    private final Project project;
    private Map<String, File> filesByPath = new HashMap<>();

    public WorkspaceContextRetriever(Project project) {
        this.project = project;
    }


    public List<Content> get() {
        try {
            List<Content> contents = new ArrayList<>();
            if (!filesByPath.isEmpty()) {
                for (Map.Entry<String, File> entry : filesByPath.entrySet()) {
                    File f = entry.getValue();

                    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(f);
                    if (virtualFile == null) continue;

                    FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(virtualFile);
                    if (fileType.isBinary()) continue;


                    if (virtualFile.getLength() > MAX_FILE_SIZE) continue;

                    try {
                        String content = Files.readString(f.toPath());
                        contents.add(Content.from(content));
                    } catch (Exception ignored) {

                    }
                }
            }


            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor == null) {
                return contents;
            }

            Document document = editor.getDocument();
            VirtualFile file = FileEditorManager.getInstance(project).getSelectedFiles()[0];
            if (file == null) {
                return contents;
            }

            if (!filesByPath.containsKey(file.getPath())) {

                String fullText = document.getText();
                int caretOffset = editor.getCaretModel().getOffset();

                int halfWindow = WINDOW_SIZE / 2;
                int start = Math.max(0, caretOffset - halfWindow);
                int end = Math.min(fullText.length(), caretOffset + halfWindow);

                String focusedText = fullText.substring(start, end);

                contents.add(Content.from(focusedText));
            }

            return contents;
        } catch (Exception e) {
            return List.of();
        }
    }

    public void addFile(File file) {
        filesByPath.put(file.getAbsolutePath(), file);
    }

    public void removeFile(File file) {
        System.err.println("here");
        filesByPath.remove(file.getAbsolutePath());
    }
}