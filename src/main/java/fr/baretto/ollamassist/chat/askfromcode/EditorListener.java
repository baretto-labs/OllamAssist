package fr.baretto.ollamassist.chat.askfromcode;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.util.Disposer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EditorListener {
    private static final Disposable PLUGIN_DISPOSABLE = Disposer.newDisposable("OllamAssistPlugin");
    private static final Map<Editor, OllamAssistSelectionListener> LISTENER_MAP =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static void attachListeners() {
        EditorFactory editorFactory = EditorFactory.getInstance();
        editorFactory.addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorCreated(@NotNull EditorFactoryEvent event) {
                handleEditorCreation(event.getEditor());
            }

            @Override
            public void editorReleased(@NotNull EditorFactoryEvent event) {
                handleEditorRelease(event.getEditor());
            }
        }, PLUGIN_DISPOSABLE);

        initializeExistingEditors();
    }

    private static void initializeExistingEditors() {
        Editor[] editors = EditorFactory.getInstance().getAllEditors();
        for (Editor editor : editors) {
            handleEditorCreation(editor);
        }
    }

    private static void handleEditorCreation(Editor editor) {
        synchronized (LISTENER_MAP) {
            if (!LISTENER_MAP.containsKey(editor)) {
                OllamAssistSelectionListener listener = new OllamAssistSelectionListener();
                editor.getSelectionModel().addSelectionListener(listener);
                LISTENER_MAP.put(editor, listener);
            }
        }
    }

    private static void handleEditorRelease(Editor editor) {
        synchronized (LISTENER_MAP) {
            OllamAssistSelectionListener listener = LISTENER_MAP.remove(editor);
            if (listener != null) {
                editor.getSelectionModel().removeSelectionListener(listener);
            }
        }
    }

    public static void dispose() {
        Disposer.dispose(PLUGIN_DISPOSABLE);
    }
}