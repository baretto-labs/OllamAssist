package fr.baretto.ollamassist.askfromcode;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager;
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager.Properties;
import fr.baretto.ollamassist.component.PromptPanel;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.KeyEvent;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OverlayPromptPanel {


    public static void showOverlayPromptPanel(Editor editor, int startOffset) {
        int offset = editor.getDocument().getLineStartOffset(startOffset);

        PromptPanel panel = createOverlayPromptPanel(editor);


        Properties properties = new Properties(
                EditorEmbeddedComponentManager.ResizePolicy.any(),
                null,
                true,
                true,
                false,
                false,
                0,
                offset
        );

        EditorEmbeddedComponentManager.getInstance().addComponent(
                (EditorEx) editor,
                panel,
                properties
        );

        EditorEmbeddedComponentManager.getInstance().addComponent((EditorEx) editor, panel, properties);

        editor.getSelectionModel().addSelectionListener(new SelectionListener() {
            @Override
            public void selectionChanged(@NotNull SelectionEvent e) {
                panel.setVisible(false);
            }
        });

    }

    private static @NotNull PromptPanel createOverlayPromptPanel(Editor editor) {
        PromptPanel panel = new PromptPanel();
        AskFromCodeAction askFromCodeAction = new AskFromCodeAction(editor, panel, editor.getSelectionModel().getSelectedText());
        panel.addActionMap(askFromCodeAction);


        Dimension editorDimension = editor.getComponent().getSize();
        Dimension dimension = panel.getPreferredSize();
        dimension.setSize(editorDimension.width * 0.6, dimension.height);
        panel.setPreferredSize(dimension);



        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();


                    if (focusOwner instanceof JTextComponent && SwingUtilities.getWindowAncestor(focusOwner) == SwingUtilities.getWindowAncestor(panel)) {
                        e.consume();
                        editor.getSelectionModel().removeSelection();
                        SwingUtilities.invokeLater(() -> panel.triggerAction(askFromCodeAction));

                        return true;
                    }
                }
                return false;
            }
        });

        return panel;
    }
}
