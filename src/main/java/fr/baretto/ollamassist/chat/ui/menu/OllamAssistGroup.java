package fr.baretto.ollamassist.chat.ui.menu;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class OllamAssistGroup extends DefaultActionGroup {

    public OllamAssistGroup() {
        super("OllamAssist", true);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        VirtualFile singleFile = e.getData(CommonDataKeys.VIRTUAL_FILE);

        boolean hasFile = (files != null && files.length > 0) || singleFile != null;
        e.getPresentation().setEnabled(hasFile);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
