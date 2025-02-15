package fr.baretto.ollamassist.askfromcode;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class EditorListener {
    private static final Disposable PLUGIN_DISPOSABLE = Disposer.newDisposable("OllamAssistPlugin");
    public static void attachListeners() {
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorCreated(@NotNull EditorFactoryEvent event) {
                Editor editor = event.getEditor();
                editor.getSelectionModel().addSelectionListener(new OllamAssistSelectionListener());
            }
        }, PLUGIN_DISPOSABLE);

        initialize();
    }


    public static void initialize() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);

            for (FileEditor fileEditor : fileEditorManager.getAllEditors()) {
                if (fileEditor instanceof TextEditor textEditor) {
                    Editor editor = textEditor.getEditor();
                    if (editor instanceof EditorEx) {
                        editor.getSelectionModel().addSelectionListener(new OllamAssistSelectionListener());
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
                                editor.getSelectionModel().addSelectionListener(new OllamAssistSelectionListener());
                            }
                        }
                    }
                }
            });
        }
    }
}
