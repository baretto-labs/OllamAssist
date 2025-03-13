package fr.baretto.ollamassist;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import fr.baretto.ollamassist.chat.askfromcode.EditorListener;
import fr.baretto.ollamassist.prerequiste.PrerequisiteService;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OllamAssistStartup implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super kotlin.Unit> continuation) {
        final PrerequisiteService prerequisiteService = ApplicationManager.getApplication().getService(PrerequisiteService.class);
        prerequisiteService.isOllamaRunningAsync().thenCombine(
                prerequisiteService.isChatModelAvailableAsync(),
                (ollamaReady, chatModelReady) -> {
                    prerequisiteService.isAutocompleteModelAvailableAsync().thenAccept(autocompleteModelReady -> {
                        if (prerequisiteService.allPrerequisitesAreAvailable(ollamaReady, chatModelReady, autocompleteModelReady)) {
                            prerequisiteService.loadModels(project);
                            EditorListener.attachListeners();
                        }
                    });
                    return null;
                }
        );
        return "EXECUTE";
    }
}
