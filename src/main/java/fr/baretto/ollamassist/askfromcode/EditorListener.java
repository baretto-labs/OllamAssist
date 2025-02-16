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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
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
        initializeEditors();
    }


    private static void initializeEditors() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
            addSelectionListenerDuringEditorOpening(fileEditorManager.getAllEditors());
            MessageBusConnection connection = project.getMessageBus().connect();
            connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
                @Override
                public void fileOpened(@NotNull FileEditorManager manager, @NotNull VirtualFile file) {
                    addSelectionListenerDuringEditorOpening(manager.getEditors(file));
                }
            });
        }
    }

    private static void addSelectionListenerDuringEditorOpening(FileEditor[] fileEditorManager) {
        for (FileEditor fileEditor : fileEditorManager) {
            if (fileEditor instanceof TextEditor textEditor) {
                Editor editor = textEditor.getEditor();
                if (editor instanceof EditorEx) {
                    editor.getSelectionModel().addSelectionListener(new OllamAssistSelectionListener());
                }
            }
        }
    }
}
