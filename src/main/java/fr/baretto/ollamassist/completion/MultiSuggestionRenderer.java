package fr.baretto.ollamassist.completion;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * Renders multiple code suggestions with navigation indicators.
 * Shows suggestion content plus navigation hints (e.g., "1 of 3 - Tab for next").
 */
public class MultiSuggestionRenderer implements EditorCustomElementRenderer {

    private static final String NAVIGATION_HINT_FORMAT = "● %d of %d - Tab: next, Shift+Tab: prev, Enter: accept, Esc: dismiss";

    private final List<String> suggestionLines;
    private final Editor editor;
    private final int currentIndex;
    private final int totalSuggestions;
    
    public MultiSuggestionRenderer(@NotNull List<String> suggestionLines, 
                                 @NotNull Editor editor,
                                 int currentIndex, 
                                 int totalSuggestions) {
        this.suggestionLines = suggestionLines;
        this.editor = editor;
        this.currentIndex = currentIndex;
        this.totalSuggestions = totalSuggestions;
    }
    
    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
        FontMetrics metrics = editor.getContentComponent().getFontMetrics(InlayUtils.getFont(editor));
        
        // Calculate width needed for suggestion content
        int maxWidth = 0;
        for (String line : suggestionLines) {
            int lineWidth = metrics.stringWidth(line);
            maxWidth = Math.max(maxWidth, lineWidth);
        }
        
        // Add width for navigation hint if multiple suggestions
        if (totalSuggestions > 1) {
            String navHint = getNavigationHint();
            int navWidth = metrics.stringWidth(navHint);
            maxWidth = Math.max(maxWidth, navWidth);
        }
        
        return maxWidth + 20; // Add some padding
    }
    
    @Override
    public int calcHeightInPixels(@NotNull Inlay inlay) {
        int lineHeight = editor.getLineHeight();
        int contentLines = suggestionLines.size();
        
        // Add extra line for navigation hint if multiple suggestions
        if (totalSuggestions > 1) {
            contentLines += 1;
        }
        
        return lineHeight * contentLines + 4; // Add some vertical padding
    }
    
    @Override
    public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle targetRegion, 
                     @NotNull TextAttributes textAttributes) {
        
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Set up drawing properties
        Font suggestionFont = InlayUtils.getFont(editor);
        g2d.setFont(suggestionFont);
        
        int lineHeight = editor.getLineHeight();
        int x = targetRegion.x + 10; // Left padding
        int y = targetRegion.y;
        
        // Draw suggestion content
        g2d.setColor(InlayUtils.getSuggestionColor());
        for (int i = 0; i < suggestionLines.size(); i++) {
            String line = suggestionLines.get(i);
            g2d.drawString(line, x, y + editor.getAscent() + (i * lineHeight));
        }
        
        // Draw navigation hint if multiple suggestions
        if (totalSuggestions > 1) {
            drawNavigationHint(g2d, x, y + (suggestionLines.size() * lineHeight) + editor.getAscent());
        }
        
        g2d.dispose();
    }
    
    /**
     * Draws navigation hint at the bottom of the suggestion.
     */
    private void drawNavigationHint(@NotNull Graphics2D g2d, int x, int y) {
        String hint = getNavigationHint();
        
        // Use a slightly different color for the hint
        g2d.setColor(JBColor.GRAY.brighter());
        
        // Make font smaller and italic for the hint
        Font originalFont = g2d.getFont();
        Font hintFont = originalFont.deriveFont(Font.ITALIC, originalFont.getSize() - 2.0f);
        g2d.setFont(hintFont);
        
        g2d.drawString(hint, x, y);
        
        // Restore original font
        g2d.setFont(originalFont);
    }
    
    /**
     * Generates the navigation hint text.
     */
    @NotNull
    private String getNavigationHint() {
        if (totalSuggestions <= 1) {
            return "";
        }
        
        return String.format(NAVIGATION_HINT_FORMAT, currentIndex, totalSuggestions);
    }
}