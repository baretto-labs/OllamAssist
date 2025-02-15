package fr.baretto.ollamassist.askfromcode;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import fr.baretto.ollamassist.chat.ui.ImageUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SelectionGutterIcon {
    private static final Map<Editor, RangeHighlighter> activeHighlighters = new HashMap<>();

    public static void addGutterIcon(@NotNull Editor editor, int startOffset) {
        MarkupModel markupModel = editor.getMarkupModel();
        int lineNumber = editor.getDocument().getLineNumber(startOffset);

        removeGutterIcon(editor);

        if (lineNumber < 0 || lineNumber >= editor.getDocument().getLineCount()) {
            return;
        }


        RangeHighlighter highlighter = markupModel.addLineHighlighter(
                lineNumber, 5000, new TextAttributes()
        );

        highlighter.setGutterIconRenderer(new GutterIconRenderer() {
            @Override
            public @NotNull Icon getIcon() {
                return ImageUtil.OLLAMASSIST_ICON;
            }

            @Override
            public AnAction getClickAction() {
                return new AnAction() {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        OverlayPopup.showPopup(editor, lineNumber);
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
        });
        activeHighlighters.put(editor, highlighter);

        addCaretListener(editor);
    }

    private static void removeGutterIcon(@NotNull Editor editor) {
        RangeHighlighter previousHighlighter = activeHighlighters.remove(editor);
        if (previousHighlighter != null) {
            editor.getMarkupModel().removeHighlighter(previousHighlighter);
        }
    }

    private static void addCaretListener(@NotNull Editor editor) {

        editor.getSelectionModel().addSelectionListener(new SelectionListener() {
            @Override
            public void selectionChanged(@NotNull SelectionEvent e) {
                if (e.getNewRange().getLength() == 0) { // Plus de sélection
                    removeGutterIcon(editor);
                }
            }
        });

        editor.getCaretModel().addCaretListener(new CaretListener() {
            @Override
            public void caretPositionChanged(@NotNull CaretEvent event) {
                int newOffset = Objects.requireNonNull(event.getCaret()).getOffset();
                int newLine = event.getEditor().getDocument().getLineNumber(newOffset);

                RangeHighlighter currentHighlighter = activeHighlighters.get(editor);
                if (currentHighlighter != null) {
                    int highlighterLine = editor.getDocument().getLineNumber(currentHighlighter.getStartOffset());
                    if (newLine != highlighterLine) {
                        removeGutterIcon(editor);
                    }
                }
            }
        });
    }
}
