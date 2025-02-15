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

import java.awt.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OverlayPopup {
    public static void showPopup(Editor editor, int startOffset) {
        int offset = editor.getDocument().getLineStartOffset(startOffset);

        PromptPanel panel = new PromptPanel();

        Dimension editorDimension = editor.getComponent().getSize();

        Dimension dimension = panel.getPreferredSize();
        dimension.setSize(editorDimension.width * 0.6, dimension.height);
panel.setPreferredSize(dimension);

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
}
