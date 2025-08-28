package fr.baretto.ollamassist.chat.ui.menu;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import fr.baretto.ollamassist.chat.rag.WorkspaceContextRetriever;
import org.jetbrains.annotations.NotNull;

import java.io.File;


public class AddToContextAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        VirtualFile file = getSelectedFile(e);
        if (file != null && !file.isDirectory()) {
            e.getProject().getService(WorkspaceContextRetriever.class).addFile(new File(file.getPath()));
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile file = getSelectedFile(e);
        e.getPresentation().setEnabled(file != null && !file.isDirectory());
    }

    private VirtualFile getSelectedFile(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files != null && files.length == 1) {
            return files[0];
        }
        return e.getData(CommonDataKeys.VIRTUAL_FILE);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
