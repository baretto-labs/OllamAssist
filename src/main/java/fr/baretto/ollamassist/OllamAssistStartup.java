package fr.baretto.ollamassist;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import fr.baretto.ollamassist.ai.AutocompleteService;
import fr.baretto.ollamassist.chat.askfromcode.EditorListener;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.events.ModelAvailableNotifier;
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
                            new Task.Backgroundable(project, "Ollamassist is starting ...", true) {
                                @Override
                                public void run(@NotNull ProgressIndicator indicator) {
                                    project.getService(OllamaService.class).init();
                                    AutocompleteService.get();

                                    ApplicationManager.getApplication()
                                            .getMessageBus()
                                            .syncPublisher(ModelAvailableNotifier.TOPIC)
                                            .onModelAvailable();
                                }
                            }.queue();
                            EditorListener.attachListeners();
                        }
                    });
                    return null;
                }
        );
        return "EXECUTE";
    }
}
