package fr.baretto.ollamassist.component;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

public class ScrollableEditorTextField extends EditorTextField {


    @Override
    protected @NotNull EditorEx createEditor() {
        EditorEx editorEx = super.createEditor();
        editorEx.setVerticalScrollbarVisible(true);
        return editorEx;
    }
}
