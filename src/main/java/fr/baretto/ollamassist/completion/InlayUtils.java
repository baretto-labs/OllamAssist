package fr.baretto.ollamassist.completion;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Utility class for IntelliJ Inlay operations.
 */
public class InlayUtils {
    
    /**
     * Gets the appropriate font for inlay rendering based on editor settings.
     */
    @NotNull
    public static Font getFont(@NotNull Editor editor) {
        Font editorFont = editor.getColorsScheme().getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN);
        if (editorFont != null) {
            // Make it slightly smaller and italic to differentiate from regular code
            return editorFont.deriveFont(Font.ITALIC, editorFont.getSize() - 1.0f);
        }
        // Fallback to default monospace font
        return new Font(Font.MONOSPACED, Font.ITALIC, 12);
    }
    
    /**
     * Gets the text color for inlay suggestions.
     */
    @NotNull
    public static Color getSuggestionColor() {
        return com.intellij.ui.JBColor.GRAY;
    }
    
    /**
     * Gets the text color for loading indicators.
     */
    @NotNull
    public static Color getLoadingColor() {
        return com.intellij.ui.JBColor.GRAY.darker();
    }
}