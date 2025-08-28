package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import dev.langchain4j.rag.content.Content;
import fr.baretto.ollamassist.component.WorkspaceFileSelectorListener;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Provides a focused window of code around the user's caret position.
 * Extracts a substring centered on the caret, with a configurable character window size.
 */
public class WorkspaceContextRetriever {

    private static final int WINDOW_SIZE = 5000;
    private static final long MAX_FILE_SIZE = 200L * 1024L;
    private final Project project;
    private Map<String, File> filesByPath = new HashMap<>();
    private final Set<WorkspaceFileSelectorListener> listeners = new HashSet<>();

    public WorkspaceContextRetriever(Project project) {
        this.project = project;
    }


    public List<Content> get() {
        Application application = ApplicationManager.getApplication();

        if (application.isReadAccessAllowed()) {
            return doGet();
        }
        return application.runReadAction((Computable<List<Content>>) this::doGet);
    }

    @NotNull List<Content> doGet() {
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
        listeners.forEach(listener -> listener.newFileAdded(file));
    }

    public void removeFile(File file) {
        filesByPath.remove(file.getAbsolutePath());
    }

    public void subscribe(WorkspaceFileSelectorListener listener) {
        listeners.add(listener);
    }
}