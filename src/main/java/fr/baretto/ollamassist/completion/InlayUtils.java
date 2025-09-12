package fr.baretto.ollamassist.completion;

import com.intellij.openapi.editor.Editor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Utility class for IntelliJ Inlay operations.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class InlayUtils {

    /**
     * Gets the appropriate font for inlay rendering based on editor settings.
     */
    @NotNull
    public static Font getFont(@NotNull Editor editor) {
        return editor.getColorsScheme().getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN);
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