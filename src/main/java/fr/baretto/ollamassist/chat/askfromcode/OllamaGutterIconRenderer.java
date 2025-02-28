package fr.baretto.ollamassist.chat.askfromcode;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import fr.baretto.ollamassist.chat.ui.IconUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class OllamaGutterIconRenderer extends GutterIconRenderer {

    private final Editor editor;
    private final int lineNumber;

    public OllamaGutterIconRenderer(Editor editor, int lineNumber){
        this.editor = editor;
        this.lineNumber = lineNumber;
    }
    @Override
    public @NotNull Icon getIcon() {
        return IconUtils.OLLAMASSIST_ICON;
    }

    @Override
    public AnAction getClickAction() {
        return new AnAction() {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                editor.getProject().getService(OverlayPromptPanelFactory.class)
                        .showOverlayPromptPanel(editor, lineNumber);
            }
        };
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof GutterIconRenderer;
    }

    @Override
    public int hashCode() {
        return getIcon().hashCode();
    }

    @Override
    public @Nullable String getTooltipText() {
        return "OllamAssist inline chat";
    }
}
