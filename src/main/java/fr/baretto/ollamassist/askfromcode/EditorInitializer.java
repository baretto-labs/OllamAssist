package fr.baretto.ollamassist.askfromcode;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

public class EditorInitializer {
    private static final Logger LOGGER = Logger.getLogger(EditorInitializer.class.getName());

    public static void initialize() {
        LOGGER.info("Initialisation des éditeurs déjà ouverts...");

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);

            for (FileEditor fileEditor : fileEditorManager.getAllEditors()) {
                if (fileEditor instanceof TextEditor textEditor) {
                    Editor editor = textEditor.getEditor();
                    if (editor instanceof EditorEx) {

                    }
                }
            }

            MessageBusConnection connection = project.getMessageBus().connect();
            connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
                @Override
                public void fileOpened(@NotNull FileEditorManager manager, @NotNull VirtualFile file) {
                    for (FileEditor fileEditor : manager.getEditors(file)) {
                        if (fileEditor instanceof TextEditor textEditor) {
                            Editor editor = textEditor.getEditor();
                            if (editor instanceof EditorEx) {

                            }
                        }
                    }
                }
            });
        }
    }
}
