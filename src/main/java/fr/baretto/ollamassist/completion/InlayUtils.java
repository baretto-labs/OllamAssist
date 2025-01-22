package fr.baretto.ollamassist.completion;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorFontType;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;

public class InlayUtils {
    private InlayUtils() {

    }

    public static Font getFont(Editor editor) {
        Font font = editor.getColorsScheme().getFont(EditorFontType.ITALIC);
        Map<TextAttribute, Object> attributes = new HashMap<>(font.getAttributes());
        return new Font(attributes);
    }

}
