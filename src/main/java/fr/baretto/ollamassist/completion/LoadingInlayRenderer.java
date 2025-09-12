package fr.baretto.ollamassist.completion;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Renders a loading indicator while the AI suggestion is being generated.
 * Shows animated dots and progress text to provide user feedback.
 */
public class LoadingInlayRenderer implements EditorCustomElementRenderer {
    
    private final Editor editor;
    private final String loadingText;
    private long startTime;
    private int dotCount = 0;
    private Timer animationTimer;
    
    public LoadingInlayRenderer(@NotNull Editor editor, @NotNull String loadingText) {
        this.editor = editor;
        this.loadingText = loadingText;
        this.startTime = System.currentTimeMillis();
        startAnimation();
    }
    
    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
        FontMetrics metrics = editor.getContentComponent().getFontMetrics(InlayUtils.getFont(editor));
        String displayText = getCurrentDisplayText();
        return metrics.stringWidth(displayText);
    }
    
    @Override
    public int calcHeightInPixels(@NotNull Inlay inlay) {
        return editor.getLineHeight();
    }
    
    @Override
    public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle targetRegion, 
                     @NotNull TextAttributes textAttributes) {
        
        g.setColor(JBColor.GRAY.darker());
        g.setFont(InlayUtils.getFont(editor));
        
        String displayText = getCurrentDisplayText();
        
        // Add slight transparency for loading state
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        
        g2d.drawString(
            displayText,
            targetRegion.x,
            targetRegion.y + editor.getAscent()
        );
        
        g2d.dispose();
    }
    
    /**
     * Gets the current display text with animated dots.
     */
    private String getCurrentDisplayText() {
        long elapsed = System.currentTimeMillis() - startTime;
        String dots = ".".repeat((dotCount % 4)); // 0 to 3 dots
        
        // Show elapsed time after 2 seconds
        if (elapsed > 2000) {
            return String.format("⏳ %s%s (%.1fs)", loadingText, dots, elapsed / 1000.0);
        } else {
            return String.format("⏳ %s%s", loadingText, dots);
        }
    }
    
    /**
     * Starts the animation timer for the loading dots.
     */
    private void startAnimation() {
        animationTimer = new Timer(500, e -> {
            dotCount++;
            // Trigger repaint by invalidating the editor
            editor.getContentComponent().repaint();
        });
        animationTimer.start();
    }
    
    /**
     * Stops the animation and cleans up resources.
     */
    public void dispose() {
        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }
    }
}