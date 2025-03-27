package fr.baretto.ollamassist.chat.askfromcode;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Slf4j
public class OllamAssistSelectionListener implements SelectionListener {

    @Override
    public void selectionChanged(@NotNull SelectionEvent e) {
        try {
            Editor editor = e.getEditor();
            if (!editor.getSelectionModel().hasSelection()) return;

            int startOffset = editor.getSelectionModel().getSelectionStart();
            Objects.requireNonNull(editor.getProject()).getService(SelectionGutterIcon.class).addGutterIcon(editor, startOffset);
        } catch (Exception ex) {
            log.warn("Exception during selection changed : {}", ex.getMessage());
        }

    }
}
