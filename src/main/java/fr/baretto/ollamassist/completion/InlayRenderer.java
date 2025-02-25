package fr.baretto.ollamassist.completion;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

public record InlayRenderer(List<String> suggestion, Editor editor) implements EditorCustomElementRenderer {

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
        String[] lines = suggestion.toString().split("\n");
        FontMetrics metrics = inlay.getEditor().getContentComponent().getFontMetrics(new Font("Source Code Pro", Font.PLAIN, 14));

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, metrics.stringWidth(line));
        }
        String firstLine = lines[0];
        return editor.getContentComponent()
                .getFontMetrics(InlayUtils.getFont(editor))
                .stringWidth(firstLine);
    }

    @Override
    public int calcHeightInPixels(Inlay inlay) {
        return editor.getLineHeight() * suggestion.size();
    }

    @Override
    public void paint(Inlay inlay, Graphics g, Rectangle targetRegion, TextAttributes textAttributes) {

        g.setColor(JBColor.GRAY);
        g.setFont(InlayUtils.getFont(editor));

        LogicalPosition lineStartPosition = new LogicalPosition(editor.getCaretModel().getVisualLineStart(), 0);
        Point point = editor.logicalPositionToXY(lineStartPosition);

        int x = targetRegion.x;
        int initialPosition = editor.getCaretModel().getVisualPosition().column;
        for (int i = 0; i < suggestion.size(); i++) {
            String line = suggestion.get(i);
            g.drawString(
                    line,
                    x,
                    targetRegion.y + i * editor.getLineHeight() + editor.getAscent()
            );
            x = point.x + initialPosition;
        }
    }
}

