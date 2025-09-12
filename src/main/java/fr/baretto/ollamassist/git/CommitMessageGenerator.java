package fr.baretto.ollamassist.git;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.ui.Refreshable;
import fr.baretto.ollamassist.chat.ui.IconUtils;
import fr.baretto.ollamassist.completion.LightModelAssistant;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

@Slf4j
public class CommitMessageGenerator extends AnAction {


    private static final Icon OLLAMASSIST_ICON = IconUtils.OLLAMASSIST_ICON;
    private static final Icon LOADING_ICON = IconUtils.OLLAMASSIST_THINKING_ICON;


    public CommitMessageGenerator() {
        super(OLLAMASSIST_ICON);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        new Task.Backgroundable(getEventProject(e), "Analyzing changes to prepare commit message…", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    e.getPresentation().setIcon(LOADING_ICON);
                    Project project = e.getProject();
                    if (project == null) return;

                    CommitMessageI commitPanel = getVcsPanel(e);

                    if (commitPanel != null) {
                        String commitMessage = generateCommitMessage(project, e);

                        ApplicationManager.getApplication().invokeLater(() ->
                                commitPanel.setCommitMessage(commitMessage)
                        );
                    }
                } catch (Exception exception) {
                    log.error("Exception during commit message generation", exception);
                } finally {
                    e.getPresentation().setIcon(OLLAMASSIST_ICON);
                }
            }
        }.queue();

    }

    private CommitMessageI getVcsPanel(AnActionEvent e) {
        if (e == null) return null;

        DataContext context = e.getDataContext();
        Object data = Refreshable.PANEL_KEY.getData(context);
        if (data instanceof CommitMessageI commitMessageI) {
            return commitMessageI;
        }
        return VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(context);
    }


    public String generateCommitMessage(Project project, AnActionEvent e) {
        // Try to get selected changes from commit panel
        SelectedChanges selectedChanges = getSelectedChanges(e);
        
        Collection<Change> changes;
        Collection<FilePath> unversionedFiles;
        
        if (selectedChanges.hasSelection()) {
            // Use only selected changes
            changes = selectedChanges.changes();
            unversionedFiles = selectedChanges.unversionedFiles();
            log.debug("Using {} selected changes and {} unversioned files for commit message", 
                changes.size(), unversionedFiles.size());
        } else {
            // Fallback to all changes if no selection
            changes = ChangeListManager.getInstance(project).getAllChanges();
            unversionedFiles = ChangeListManager.getInstance(project).getUnversionedFilesPaths();
            log.debug("No selection found, using all {} changes and {} unversioned files for commit message", 
                changes.size(), unversionedFiles.size());
        }
        
        String gitDiff = DiffGenerator.getDiff(changes, java.util.List.copyOf(unversionedFiles));
        return MessageCleaner.clean(LightModelAssistant.get().writecommitMessage(gitDiff));
    }
    
    /**
     * Attempts to retrieve selected changes from the commit panel using reflection.
     * Returns selected changes if available, otherwise returns empty selection.
     */
    private SelectedChanges getSelectedChanges(AnActionEvent e) {
        if (e == null) {
            return SelectedChanges.empty();
        }
        
        DataContext context = e.getDataContext();
        
        // Try different strategies using reflection to access internal APIs
        try {
            // Strategy 1: Try COMMIT_WORKFLOW_HANDLER
            Object workflowHandler = VcsDataKeys.COMMIT_WORKFLOW_HANDLER.getData(context);
            if (workflowHandler != null) {
                SelectedChanges result = tryGetChangesViaReflection(workflowHandler, "workflow handler");
                if (result.hasSelection()) {
                    return result;
                }
            }
            
            // Strategy 2: Try Refreshable.PANEL_KEY  
            Object panel = Refreshable.PANEL_KEY.getData(context);
            if (panel != null) {
                SelectedChanges result = tryGetChangesViaReflection(panel, "panel");
                if (result.hasSelection()) {
                    return result;
                }
            }
            
        } catch (Exception ex) {
            log.debug("Failed to get selected changes via reflection", ex);
        }
        
        log.debug("No selected changes found, will use all changes");
        return SelectedChanges.empty();
    }
    
    /**
     * Attempts to extract changes from an object using reflection
     */
    @SuppressWarnings("unchecked")
    SelectedChanges tryGetChangesViaReflection(Object source, String sourceName) {
        try {
            Class<?> clazz = source.getClass();
            
            // Try common method names for getting included/selected changes
            String[] changeMethods = {"getIncludedChanges", "getSelectedChanges", "getAllChanges"};
            String[] unversionedMethods = {"getIncludedUnversionedFiles", "getUnversionedFiles"};
            
            Collection<Change> changes = null;
            Collection<FilePath> unversionedFiles = null;
            
            // Try to get changes
            for (String methodName : changeMethods) {
                try {
                    var method = clazz.getMethod(methodName);
                    Object result = method.invoke(source);
                    if (result instanceof Collection<?> collection) {
                        changes = (Collection<Change>) collection;
                        log.debug("Found changes using {}.{}: {} items", sourceName, methodName, changes.size());
                        break;
                    }
                } catch (Exception ignored) {
                    // Try next method
                }
            }
            
            // Try to get unversioned files
            for (String methodName : unversionedMethods) {
                try {
                    var method = clazz.getMethod(methodName);
                    Object result = method.invoke(source);
                    if (result instanceof Collection<?> collection) {
                        unversionedFiles = (Collection<FilePath>) collection;
                        log.debug("Found unversioned files using {}.{}: {} items", sourceName, methodName, unversionedFiles.size());
                        break;
                    }
                } catch (Exception ignored) {
                    // Try next method
                }
            }
            
            // If we found anything, return it
            if ((changes != null && !changes.isEmpty()) || (unversionedFiles != null && !unversionedFiles.isEmpty())) {
                Collection<Change> finalChanges = changes != null ? changes : java.util.Collections.emptyList();
                Collection<FilePath> finalUnversioned = unversionedFiles != null ? unversionedFiles : java.util.Collections.emptyList();
                
                log.debug("Successfully extracted selection from {}: {} changes, {} unversioned files", 
                    sourceName, finalChanges.size(), finalUnversioned.size());
                return new SelectedChanges(finalChanges, finalUnversioned, true);
            }
            
        } catch (Exception ex) {
            log.debug("Reflection failed for {}", sourceName, ex);
        }
        
        return SelectedChanges.empty();
    }
    
    /**
     * Container for selected changes and unversioned files
     */
    public record SelectedChanges(
        Collection<Change> changes,
        Collection<FilePath> unversionedFiles,
        boolean hasSelection
    ) {
        static SelectedChanges empty() {
            return new SelectedChanges(java.util.Collections.emptyList(), java.util.Collections.emptyList(), false);
        }
    }

}