package fr.baretto.ollamassist.chat.askfromcode;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import org.jetbrains.annotations.NotNull;

public class OllamAssistSelectionListener implements SelectionListener {

    @Override
    public void selectionChanged(@NotNull SelectionEvent e) {
        Editor editor = e.getEditor();
        if (!editor.getSelectionModel().hasSelection()) return;

        int startOffset = editor.getSelectionModel().getSelectionStart();

        SelectionGutterIcon.addGutterIcon(editor, startOffset);
    }
}
