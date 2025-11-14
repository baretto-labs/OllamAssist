package fr.baretto.ollamassist.completion;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Manages multiple code completion suggestions with Tab/Shift+Tab navigation.
 * Provides a more advanced user experience similar to modern IDEs.
 */
@Slf4j
public class MultiSuggestionManager {
    
    private Inlay<?> currentInlay;
    private Inlay<?> loadingInlay;
    private LoadingInlayRenderer loadingRenderer;
    
    private List<String> suggestions = new ArrayList<>();
    private int currentSuggestionIndex = 0;
    private boolean hasMultipleSuggestions = false;
    
    /**
     * Shows a loading indicator while suggestions are being generated.
     */
    public void showLoading(@NotNull Editor editor, int offset, @NotNull String loadingMessage) {
        disposeCurrentInlay();
        disposeLoadingInlay();
        
        Application application = ApplicationManager.getApplication();
        if (application == null) {
            // Running in test environment, execute directly
            InlayModel inlayModel = editor.getInlayModel();
            loadingRenderer = new LoadingInlayRenderer(editor, loadingMessage);
            loadingInlay = inlayModel.addInlineElement(offset, false, loadingRenderer);
            return;
        }
        
        application.invokeLater(() -> {
            InlayModel inlayModel = editor.getInlayModel();
            loadingRenderer = new LoadingInlayRenderer(editor, loadingMessage);
            loadingInlay = inlayModel.addInlineElement(offset, false, loadingRenderer);
        });
    }
    
    /**
     * Shows a single suggestion (fallback mode).
     */
    public void showSuggestion(@NotNull Editor editor, int offset, @NotNull String suggestion) {
        log.info("🎭 MultiSuggestionManager.showSuggestion called with: '{}'", suggestion);
        showSuggestions(editor, offset, List.of(suggestion));
    }
    
    /**
     * Shows multiple suggestions with navigation support.
     */
    public void showSuggestions(@NotNull Editor editor, int offset, @NotNull List<String> suggestionList) {
        log.info("🎭 MultiSuggestionManager.showSuggestions called with {} suggestions at offset {}", suggestionList.size(), offset);
        
        disposeLoadingInlay();
        disposeCurrentInlay();
        
        if (suggestionList.isEmpty()) {
            log.warn("No suggestions to display - suggestionList is empty");
            return;
        }
        
        this.suggestions = new ArrayList<>(suggestionList);
        this.currentSuggestionIndex = 0;
        this.hasMultipleSuggestions = suggestionList.size() > 1;
        
        log.info("📋 About to display current suggestion: '{}'", suggestions.get(currentSuggestionIndex));
        displayCurrentSuggestion(editor, offset);
        
        log.info("✅ Successfully set up {} suggestion(s), current index: {}", suggestions.size(), currentSuggestionIndex);
    }
    
    /**
     * Navigates to the next suggestion (Tab key).
     */
    public boolean nextSuggestion(@NotNull Editor editor) {
        if (!hasMultipleSuggestions || suggestions.isEmpty()) {
            return false;
        }
        
        currentSuggestionIndex = (currentSuggestionIndex + 1) % suggestions.size();
        displayCurrentSuggestion(editor, editor.getCaretModel().getOffset());
        
        log.debug("Navigated to suggestion {} of {}", currentSuggestionIndex + 1, suggestions.size());
        return true;
    }
    
    /**
     * Navigates to the previous suggestion (Shift+Tab).
     */
    public boolean previousSuggestion(@NotNull Editor editor) {
        if (!hasMultipleSuggestions || suggestions.isEmpty()) {
            return false;
        }
        
        currentSuggestionIndex = (currentSuggestionIndex - 1 + suggestions.size()) % suggestions.size();
        displayCurrentSuggestion(editor, editor.getCaretModel().getOffset());
        
        log.debug("Navigated to suggestion {} of {}", currentSuggestionIndex + 1, suggestions.size());
        return true;
    }
    
    /**
     * Displays the current suggestion with navigation hints.
     */
    private void displayCurrentSuggestion(@NotNull Editor editor, int offset) {
        log.info("🎨 displayCurrentSuggestion called with offset: {}", offset);
        disposeCurrentInlay();
        
        Application application = ApplicationManager.getApplication();
        if (application == null) {
            // Running in test environment, execute directly
            displaySuggestionInlay(editor, offset);
            return;
        }
        
        application.invokeLater(() -> {
            displaySuggestionInlay(editor, offset);
        });
    }
    
    private void displaySuggestionInlay(@NotNull Editor editor, int offset) {
        try {
            String currentSuggestion = suggestions.get(currentSuggestionIndex);
            log.info("🎨 Creating inlay for suggestion: '{}'", currentSuggestion);
            
            List<String> lines = Arrays.asList(currentSuggestion.split("\n"));
            log.info("🎨 Split into {} lines", lines.size());
            
            InlayModel inlayModel = editor.getInlayModel();
            
            // Create renderer with navigation info
            MultiSuggestionRenderer renderer = new MultiSuggestionRenderer(
                lines, 
                editor,
                hasMultipleSuggestions ? currentSuggestionIndex + 1 : 0,
                suggestions.size()
            );
            
            log.info("🎨 Adding block element to InlayModel at offset: {}", offset);
            currentInlay = inlayModel.addBlockElement(offset, true, false, 0, renderer);
            
            if (currentInlay != null) {
                log.info("✅ Successfully created inlay: {}", currentInlay);
            } else {
                log.error("❌ Failed to create inlay - addBlockElement returned null");
            }
            
        } catch (Exception e) {
            log.error("❌ Error creating inlay display", e);
        }
    }
    
    /**
     * Inserts the currently selected suggestion into the editor.
     */
    public void insertCurrentSuggestion(@NotNull Editor editor) {
        log.debug("insertCurrentSuggestion() called");
        if (suggestions.isEmpty()) {
            log.debug("No suggestions available to insert");
            log.debug("No suggestion to insert");
            return;
        }
        
        String suggestionToInsert = suggestions.get(currentSuggestionIndex);
        log.debug("About to insert suggestion: '{}'", suggestionToInsert.substring(0, Math.min(50, suggestionToInsert.length())));
        
        ApplicationManager.getApplication().runWriteAction(() -> 
            CommandProcessor.getInstance().executeCommand(editor.getProject(), () -> {
                try {
                    Document document = editor.getDocument();
                    CaretModel caretModel = editor.getCaretModel();
                    int caretOffset = caretModel.getOffset();

                    document.insertString(caretOffset, suggestionToInsert);

                    // Format the inserted code
                    if (editor.getProject() != null) {
                        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(editor.getProject());
                        psiDocumentManager.commitDocument(document);
                        CodeStyleManager.getInstance(editor.getProject()).reformatText(
                                psiDocumentManager.getPsiFile(document), 
                                caretOffset, 
                                caretOffset + suggestionToInsert.length()
                        );
                    }

                    caretModel.moveToOffset(caretOffset + suggestionToInsert.length());

                    clearSuggestions();
                    
                    log.debug("Inserted suggestion {} ({} chars)", currentSuggestionIndex + 1, suggestionToInsert.length());
                    
                } catch (Exception e) {
                    log.error("Error inserting suggestion", e);
                }
            }, "Insert AI Suggestion", null)
        );
    }
    
    /**
     * Returns the current suggestion text.
     */
    @Nullable
    public String getCurrentSuggestion() {
        if (suggestions.isEmpty()) {
            return null;
        }
        return suggestions.get(currentSuggestionIndex);
    }
    
    /**
     * Checks if there are active suggestions.
     */
    public boolean hasSuggestions() {
        return !suggestions.isEmpty();
    }
    
    /**
     * Checks if multiple suggestions are available for navigation.
     */
    public boolean hasMultipleSuggestions() {
        return hasMultipleSuggestions;
    }
    
    /**
     * Gets the current suggestion index (1-based for display).
     */
    public int getCurrentSuggestionNumber() {
        return suggestions.isEmpty() ? 0 : currentSuggestionIndex + 1;
    }
    
    /**
     * Gets the total number of suggestions.
     */
    public int getTotalSuggestions() {
        return suggestions.size();
    }
    
    /**
     * Clears all suggestions and disposes UI elements.
     */
    public void clearSuggestions() {
        suggestions.clear();
        currentSuggestionIndex = 0;
        hasMultipleSuggestions = false;
        disposeCurrentInlay();
        disposeLoadingInlay();
    }
    
    /**
     * Disposes the current suggestion inlay.
     */
    public void disposeCurrentInlay() {
        if (currentInlay != null) {
            Application application = ApplicationManager.getApplication();
            if (application == null) {
                // Running in test environment, dispose directly
                currentInlay.dispose();
                currentInlay = null;
                return;
            }
            
            application.invokeLater(() -> {
                if (currentInlay != null) {
                    currentInlay.dispose();
                    currentInlay = null;
                }
            });
        }
    }
    
    /**
     * Disposes the loading indicator inlay.
     */
    public void disposeLoadingInlay() {
        if (loadingInlay != null) {
            Application application = ApplicationManager.getApplication();
            if (application == null) {
                // Running in test environment, dispose directly
                loadingInlay.dispose();
                loadingInlay = null;
            } else {
                application.invokeLater(() -> {
                    if (loadingInlay != null) {
                        loadingInlay.dispose();
                        loadingInlay = null;
                    }
                });
            }
        }
        
        if (loadingRenderer != null) {
            loadingRenderer.dispose();
            loadingRenderer = null;
        }
    }
    
    /**
     * Gets debugging information about the current state.
     */
    @NotNull
    public String getDebugInfo() {
        return String.format(
            "MultiSuggestionManager{suggestions=%d, current=%d, hasMultiple=%b, hasInlay=%b}",
            suggestions.size(),
            currentSuggestionIndex,
            hasMultipleSuggestions,
            currentInlay != null
        );
    }
}