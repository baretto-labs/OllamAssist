package fr.baretto.ollamassist.actions.refactor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class RefactoringInlayRenderer implements EditorCustomElementRenderer {

    private final List<String> lines;
    private final Color backgroundColor;
    @Setter
    private Point mousePosition = null;

    public Rectangle acceptBounds;
    public Rectangle declineBounds;

    public RefactoringInlayRenderer(@NotNull String text, @NotNull Color backgroundColor) {
        this.lines = Arrays.asList(text.split("\n"));
        this.backgroundColor = backgroundColor;
    }


    private Font getFont(Editor editor) {
        return editor.getColorsScheme().getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN);
    }

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
        return inlay.getEditor().getComponent().getWidth();
    }

    @Override
    public int calcHeightInPixels(@NotNull Inlay inlay) {
        FontMetrics metrics = inlay.getEditor().getComponent().getFontMetrics(getFont(inlay.getEditor()));
        int lineHeight = metrics.getHeight();
        return (lines.size() * lineHeight) + (lineHeight + 10) + 10;
    }

    @Override
    public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(backgroundColor);
            g2.fill(r);
            g2.setColor(JBColor.border());
            g2.drawRect(r.x, r.y, r.width - 1, r.height - 1);

            g2.setColor(JBColor.foreground());
            g2.setFont(getFont(inlay.getEditor()));
            FontMetrics metrics = g2.getFontMetrics();
            int lineHeight = metrics.getHeight();
            int ascent = metrics.getAscent();
            int margin = 10;

            for (int i = 0; i < lines.size(); i++) {
                g2.drawString(lines.get(i), r.x + margin, r.y + margin + ascent + (i * lineHeight));
            }

            int actionsY = r.y + margin + (lines.size() * lineHeight) + 5;
            String acceptText = " Accept ";
            String declineText = " Decline ";
            int buttonHeight = lineHeight + 4;


            int acceptWidth = metrics.stringWidth(acceptText);
            int declineWidth = metrics.stringWidth(declineText);
            int totalWidth = acceptWidth + declineWidth + margin;

            int startX = r.x + (r.width - totalWidth) / 2;

            acceptBounds = new Rectangle(startX, actionsY, acceptWidth, buttonHeight);
            declineBounds = new Rectangle(startX + acceptWidth + margin, actionsY, declineWidth, buttonHeight);

            drawButton(g2, acceptText, acceptBounds, mousePosition);

            drawButton(g2, declineText, declineBounds, mousePosition);

        } finally {
            g2.dispose();
        }
    }

    private void drawButton(Graphics2D g2, String text, Rectangle bounds, Point mousePos) {
        boolean hovered = mousePos != null && bounds.contains(mousePos);
        g2.setColor(hovered ? UIUtil.getButtonSelectColor().brighter() : UIUtil.getButtonSelectColor());

        g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g2.setColor(JBColor.border());
        g2.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, 8, 8);

        FontMetrics metrics = g2.getFontMetrics();
        int textX = bounds.x + (bounds.width - metrics.stringWidth(text)) / 2;
        int textY = bounds.y + (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent();
        g2.setColor(JBColor.foreground());
        g2.drawString(text, textX, textY);
    }
}