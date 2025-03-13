package fr.baretto.ollamassist.chat.askfromcode;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager;
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager.Properties;
import com.intellij.openapi.util.Disposer;
import fr.baretto.ollamassist.component.PromptPanel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.WeakHashMap;


public class OverlayPromptPanelFactory {

    private final Map<Editor, Inlay<?>> activeInlays = new WeakHashMap<>();
    private static final PromptPanel PROMPT_PANEL = new PromptPanel();
    private static final AskFromCodeAction ASK_FROM_CODE_ACTION = new AskFromCodeAction(PROMPT_PANEL);

    static {
        KeyAdapter keyListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    PROMPT_PANEL.getSendButton().doClick();
                    e.consume();
                }
            }
        };
        PROMPT_PANEL.getEditorTextField().addKeyListener(keyListener);
    }


    public void showOverlayPromptPanel(Editor editor, int startOffset) {
        updateOverlayPromptPanel(editor);

        Properties properties = new Properties(
                EditorEmbeddedComponentManager.ResizePolicy.any(),
                null,
                false,
                true,
                false,
                false,
                1000,
                editor.getDocument().getLineStartOffset(startOffset)
        );

        Inlay<?> inlay = EditorEmbeddedComponentManager.getInstance().addComponent(
                (EditorEx) editor,
                PROMPT_PANEL,
                properties
        );

        activeInlays.put(editor, inlay);

        SelectionListener selectionListener = new SelectionListener() {
            @Override
            public void selectionChanged(@NotNull SelectionEvent e) {
                closeActivePanel(editor, this);
            }
        };
        editor.getSelectionModel().addSelectionListener(selectionListener, PROMPT_PANEL);

        if (editor instanceof Disposable disposable) {
            Disposer.register(disposable, () -> closeActivePanel(editor, selectionListener));
        }
    }

    private void updateOverlayPromptPanel(Editor editor) {

        ASK_FROM_CODE_ACTION.fromCodeEditor(editor);
        PROMPT_PANEL.addActionMap(ASK_FROM_CODE_ACTION);

        Dimension editorDimension = editor.getComponent().getSize();
        Dimension dimension = PROMPT_PANEL.getPreferredSize();
        dimension.setSize(editorDimension.width * 0.6, dimension.height * 2d);
        PROMPT_PANEL.setPreferredSize(dimension);
    }

    public void closeActivePanel(Editor editor, SelectionListener selectionListener) {
        Inlay<?> inlay = activeInlays.get(editor);
        if (inlay != null) {
            inlay.dispose();
            activeInlays.remove(editor);
            editor.getSelectionModel().removeSelectionListener(selectionListener);
        }
    }
}