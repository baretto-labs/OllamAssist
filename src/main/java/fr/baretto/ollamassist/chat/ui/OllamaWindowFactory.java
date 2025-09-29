package fr.baretto.ollamassist.chat.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class OllamaWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project,
                                        @NotNull ToolWindow toolWindow) {

        ContentFactory contentFactory = ContentFactory.getInstance();

        // Chat classique avec agent intégré
        OllamaContent ollamaContent = new OllamaContent(toolWindow);
        Content chatContent = contentFactory
                .createContent(ollamaContent.getContentPanel(), "Chat", false);
        toolWindow.getContentManager().addContent(chatContent);
    }
}