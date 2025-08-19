package fr.baretto.ollamassist.chat.askfromcode;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.awt.event.HierarchyEvent;
import java.util.Map;
import java.util.WeakHashMap;

@Slf4j
public class SelectionGutterIcon {

    private final Map<Editor, Disposable> editorDisposables = new WeakHashMap<>();
    private final Map<Editor, RangeHighlighter> activeHighlighters = new WeakHashMap<>();
    private final OllamaGutterIconRenderer gutterIconRenderer = new OllamaGutterIconRenderer();

    public void addGutterIcon(@NotNull Editor editor, int startOffset) {
        MarkupModel markupModel = editor.getMarkupModel();

        int lineNumber = editor.getDocument().getLineNumber(startOffset);

        removeGutterIcon(editor);

        if (lineNumber < 0 || lineNumber >= editor.getDocument().getLineCount()) {
            return;
        }

        Disposable parentDisposable = getOrCreateEditorDisposable(editor);

        RangeHighlighter highlighter = markupModel.addLineHighlighter(
                lineNumber,
                5000,
                new TextAttributes()
        );

        gutterIconRenderer.update(editor, lineNumber);
        highlighter.setGutterIconRenderer(gutterIconRenderer);
        activeHighlighters.put(editor, highlighter);

        Disposer.register(parentDisposable, () -> {
            markupModel.removeHighlighter(highlighter);
            activeHighlighters.remove(editor);
        });

        setupListeners(editor, parentDisposable);
    }

    private Disposable getOrCreateEditorDisposable(Editor editor) {
        return editorDisposables.computeIfAbsent(editor, k -> {
            Disposable disposable = Disposer.newDisposable("GutterIconCleanup");

            Disposer.register(getProjectDisposable(editor), disposable);

            editor.getComponent().addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0 && editor.getComponent().getParent() == null) {
                    Disposer.dispose(disposable);
                }
            });

            return disposable;
        });
    }

    private Disposable getProjectDisposable(Editor editor) {
        Project project = editor.getProject();
        return project != null ? project : Disposer.newDisposable("app");
    }

    private void setupListeners(Editor editor, Disposable parentDisposable) {
        SelectionListener selectionListener = new SelectionListener() {
            @Override
            public void selectionChanged(@NotNull SelectionEvent e) {
                if (e.getNewRange().getLength() == 0) {
                    removeGutterIcon(editor);
                }
            }
        };
        editor.getSelectionModel().addSelectionListener(selectionListener);
        Disposer.register(parentDisposable, () ->
                editor.getSelectionModel().removeSelectionListener(selectionListener)
        );

        CaretListener caretListener = new CaretListener() {
            @Override
            public void caretPositionChanged(@NotNull CaretEvent event) {
                try {
                    RangeHighlighter highlighter = activeHighlighters.get(editor);
                    if (highlighter != null && event.getCaret() != null) {
                        int currentLine = editor.getDocument().getLineNumber(event.getCaret().getOffset());
                        int highlighterLine = editor.getDocument().getLineNumber(highlighter.getStartOffset());

                        if (currentLine != highlighterLine) {
                            removeGutterIcon(editor);
                        }
                    }
                } catch (Exception e) {
                    log.debug("exception during removing gutter icon", e);
                }
            }

        };
        editor.getCaretModel().addCaretListener(caretListener);
        Disposer.register(parentDisposable, () ->
                editor.getCaretModel().removeCaretListener(caretListener)
        );
    }

    public void removeGutterIcon(@NotNull Editor editor) {
        Disposable disposable = editorDisposables.get(editor);
        if (disposable != null && !Disposer.isDisposed(disposable)) {
            Disposer.dispose(disposable);
            editorDisposables.remove(editor);
        }
    }

}