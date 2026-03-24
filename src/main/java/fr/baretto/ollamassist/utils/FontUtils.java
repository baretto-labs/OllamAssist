package fr.baretto.ollamassist.utils;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import fr.baretto.ollamassist.setting.OllamAssistUISettings;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for managing fonts throughout the plugin.
 * Provides semantic font methods that respect IDE settings and apply user-configured multiplier.
 */
public class FontUtils {

    private static final Map<String, Font> FONT_CACHE = new ConcurrentHashMap<>();
    private static float currentMultiplier = 1.0f;

    /**
     * Get the base font (respects IDE Look & Feel).
     */
    public static Font getBaseFont() {
        return getCachedFont("base", () -> UIUtil.getLabelFont());
    }

    /**
     * Get title font (base × 1.3).
     */
    public static Font getTitleFont() {
        return getCachedFont("title", () -> deriveWithRelativeSize(getBaseFont(), 1.3f));
    }

    /**
     * Get subtitle font (base × 1.1).
     */
    public static Font getSubtitleFont() {
        return getCachedFont("subtitle", () -> deriveWithRelativeSize(getBaseFont(), 1.1f));
    }

    /**
     * Get normal font (base × 1.0).
     */
    public static Font getNormalFont() {
        return getCachedFont("normal", () -> getBaseFont());
    }

    /**
     * Get small font (base × 0.85).
     */
    public static Font getSmallFont() {
        return getCachedFont("small", () -> deriveWithRelativeSize(getBaseFont(), 0.85f));
    }

    /**
     * Get code font from IDE editor settings.
     */
    public static Font getCodeFont() {
        return getCachedFont("code", () -> getIDECodeFont());
    }

    /**
     * Get font from editor's color scheme.
     */
    public static Font getEditorFont(Editor editor) {
        if (editor == null) {
            return getCodeFont();
        }
        EditorColorsScheme scheme = editor.getColorsScheme();
        return deriveWithRelativeSize(scheme.getFont(EditorFontType.PLAIN), currentMultiplier);
    }

    /**
     * Derive a font with a relative size multiplier.
     * For example, multiplier=1.5f will make the font 50% larger.
     */
    public static Font deriveWithRelativeSize(Font base, float multiplier) {
        if (base == null) {
            base = UIUtil.getLabelFont();
        }
        float size = base.getSize() * multiplier * currentMultiplier;
        return base.deriveFont(size);
    }

    /**
     * Clear the font cache. Should be called when settings change.
     */
    public static void clearCache() {
        FONT_CACHE.clear();
    }

    /**
     * Update the multiplier from settings. Should be called when settings change.
     */
    public static void updateMultiplier() {
        OllamAssistUISettings settings = OllamAssistUISettings.getInstance();
        if (settings != null && settings.getState() != null) {
            currentMultiplier = Math.max(0.5f, Math.min(2.0f, settings.getState().fontSizeMultiplier));
        } else {
            currentMultiplier = 1.0f;
        }
        clearCache();
    }

    /**
     * Get current font size multiplier.
     */
    public static float getCurrentMultiplier() {
        return currentMultiplier;
    }

    /**
     * Get IDE code font with multiplier applied.
     */
    private static Font getIDECodeFont() {
        Font editorFont = UIUtil.getLabelFont();
        try {
            // Try to get font from scheme if available
            editorFont = UIUtil.getLabelFont();
        } catch (Exception e) {
            // Fall back to label font
        }
        return deriveWithRelativeSize(editorFont, 0.9f); // Code font typically slightly smaller
    }

    /**
     * Cached font retrieval to avoid repeated calculations.
     */
    private static Font getCachedFont(String key, FontSupplier supplier) {
        String cacheKey = key + "_" + currentMultiplier;
        Font cached = FONT_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Font font = supplier.getFont();
        FONT_CACHE.put(cacheKey, font);
        return font;
    }

    /**
     * Functional interface for font suppliers.
     */
    @FunctionalInterface
    private interface FontSupplier {
        Font getFont();
    }
}
