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
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.WeakHashMap;

@NoArgsConstructor
public class OverlayPromptPanelFactory {

    private final Map<Editor, Inlay<?>> activeInlays = new WeakHashMap<>();

    public void showOverlayPromptPanel(Editor editor, int startOffset) {
        closeActivePanel(editor);

        PromptPanel panel = createOverlayPromptPanel(editor);
        KeyAdapter keyListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    panel.getSendButton().doClick();
                    e.consume();
                }
            }
        };
        panel.getEditorTextField().addKeyListener(keyListener);

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
                panel,
                properties
        );

        activeInlays.put(editor, inlay);

        SelectionListener selectionListener = new SelectionListener() {
            @Override
            public void selectionChanged(@NotNull SelectionEvent e) {
                editor.getSelectionModel().removeSelectionListener(this);
                closeActivePanel(editor);
            }
        };
        editor.getSelectionModel().addSelectionListener(selectionListener, panel);

        if (editor instanceof Disposable disposable) {
            Disposer.register(disposable, () -> closeActivePanel(editor));
        }
    }

    private @NotNull PromptPanel createOverlayPromptPanel(Editor editor) {
        PromptPanel panel = new PromptPanel();
        AskFromCodeAction askFromCodeAction = new AskFromCodeAction(editor, panel, editor.getSelectionModel().getSelectedText());
        panel.addActionMap(askFromCodeAction);

        Dimension editorDimension = editor.getComponent().getSize();
        Dimension dimension = panel.getPreferredSize();
        dimension.setSize(editorDimension.width * 0.6, dimension.height * 2d);
        panel.setPreferredSize(dimension);

        return panel;
    }

    public void closeActivePanel(Editor editor) {
        Inlay<?> inlay = activeInlays.get(editor);
        if (inlay != null) {
            inlay.dispose();
            activeInlays.remove(editor);
        }
    }
}