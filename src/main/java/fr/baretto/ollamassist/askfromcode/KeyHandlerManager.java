package fr.baretto.ollamassist.askfromcode;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.ui.components.JBTextArea;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KeyHandlerManager {
    private static EditorActionHandler originalBackspaceHandler;
    private static EditorActionHandler originalDeleteHandler;
    private static boolean isHandlingKey = false;

    public static void installHandlers(JBTextArea textArea, JPanel panel, Editor editor) {
        EditorActionManager actionManager = EditorActionManager.getInstance();

        originalBackspaceHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE);
        originalDeleteHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_DELETE);

        // Handler personnalisé pour Backspace
        actionManager.setActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE, new EditorActionHandler() {
            @Override
            public void execute(@NotNull Editor editor, DataContext dataContext) {
                if (isHandlingKey) return;

                Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (SwingUtilities.isDescendingFrom(focusOwner, panel)) {
                    handleBackspace(textArea);
                } else {
                    originalBackspaceHandler.execute(editor, dataContext);
                }
            }
        });

        // Handler personnalisé pour Delete
        actionManager.setActionHandler(IdeActions.ACTION_EDITOR_DELETE, new EditorActionHandler() {
            @Override
            public void execute(@NotNull Editor editor, DataContext dataContext) {
                if (isHandlingKey) return;

                Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (SwingUtilities.isDescendingFrom(focusOwner, panel)) {
                    handleDelete(textArea);
                } else {
                    originalDeleteHandler.execute(editor, dataContext);
                }
            }
        });
    }

    private static void handleBackspace(JBTextArea textArea) {
        isHandlingKey = true;
        try {
            int caretPos = textArea.getCaretPosition();
            if (caretPos > 0) {
                Document doc = textArea.getDocument();
                doc.remove(caretPos - 1, 1);
                textArea.setCaretPosition(caretPos - 1);
            }
        } catch (BadLocationException e) {
            // Gestion d'erreur
        } finally {
            isHandlingKey = false;
        }
    }

    private static void handleDelete(JBTextArea textArea) {
        isHandlingKey = true;
        try {
            int caretPos = textArea.getCaretPosition();
            Document doc = textArea.getDocument();
            if (caretPos < doc.getLength()) {
                doc.remove(caretPos, 1);
            }
        } catch (BadLocationException e) {
            // Gestion d'erreur
        } finally {
            isHandlingKey = false;
        }
    }

    public static void restoreHandlers() {
        EditorActionManager actionManager = EditorActionManager.getInstance();
        actionManager.setActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE, originalBackspaceHandler);
        actionManager.setActionHandler(IdeActions.ACTION_EDITOR_DELETE, originalDeleteHandler);
    }
}
