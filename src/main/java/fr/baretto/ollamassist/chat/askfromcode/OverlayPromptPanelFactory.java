package fr.baretto.ollamassist.chat.askfromcode;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager;
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager.Properties;
import fr.baretto.ollamassist.component.PromptPanel;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OverlayPromptPanelFactory {

    private static EditorActionHandler originalEnterHandler;

    public static void showOverlayPromptPanel(Editor editor, int startOffset) {
        int offset = editor.getDocument().getLineStartOffset(startOffset);

        PromptPanel panel = createOverlayPromptPanel(editor);
        panel.getEditorTextField().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    panel.getSendButton().doClick();
                    e.consume();
                }
            }
        });

        Properties properties = new Properties(
                EditorEmbeddedComponentManager.ResizePolicy.any(),
                null,
                false,
                true,
                false,
                false,
                1000,
                offset
        );

        EditorEmbeddedComponentManager.getInstance().addComponent(
                (EditorEx) editor,
                panel,
                properties
        );

        editor.getSelectionModel().addSelectionListener(new SelectionListener() {
            @Override
            public void selectionChanged(@NotNull SelectionEvent e) {
                editor.getSelectionModel().removeSelectionListener(this);
                panel.setVisible(false);
            }
        }, panel);
    }

    private static @NotNull PromptPanel createOverlayPromptPanel(Editor editor) {
        PromptPanel panel = new PromptPanel();
        AskFromCodeAction askFromCodeAction = new AskFromCodeAction(editor, panel, editor.getSelectionModel().getSelectedText());
        panel.addActionMap(askFromCodeAction);


        Dimension editorDimension = editor.getComponent().getSize();
        Dimension dimension = panel.getPreferredSize();
        dimension.setSize(editorDimension.width * 0.6, dimension.height * 2);
        panel.setPreferredSize(dimension);

        return panel;
    }

}
