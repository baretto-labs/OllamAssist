package fr.baretto.ollamassist.actions.refactor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class LineDiffHighlighter {
    private final Editor editor;
    private final TextRange range;
    private RangeHighlighter highlighter;

    public LineDiffHighlighter(@NotNull Editor editor, @NotNull TextRange range) {
        this.editor = editor;
        this.range = range;
    }

    public void apply() {
        // Nettoie au cas où il y aurait un ancien highlighter
        clear();

        TextAttributes attributes = new TextAttributes();
        // Un fond rouge clair, non agressif
        attributes.setBackgroundColor(new JBColor(new Color(255, 220, 220), new Color(90, 45, 45)));

        highlighter = editor.getMarkupModel().addRangeHighlighter(
                range.getStartOffset(),
                range.getEndOffset(),
                HighlighterLayer.CARET_ROW - 1,
                attributes,
                HighlighterTargetArea.EXACT_RANGE
        );
    }

    public void clear() {
        if (highlighter != null) {
            editor.getMarkupModel().removeHighlighter(highlighter);
            highlighter = null;
        }
    }
}