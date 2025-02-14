package fr.baretto.ollamassist.ai;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.util.messages.MessageBusConnection;
import fr.baretto.ollamassist.chat.service.OllamaService;
import fr.baretto.ollamassist.events.ModelAvailableNotifier;
import fr.baretto.ollamassist.events.UIAvailableNotifier;
import fr.baretto.ollamassist.setting.ConfigurationPanel;
import fr.baretto.ollamassist.setting.OllamassistSettingsConfigurable;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StartupOllamassist implements ProjectActivity {

    public StartupOllamassist() {
        MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
        connection.subscribe(UIAvailableNotifier.TOPIC, (UIAvailableNotifier) this::loadModelsAndNotify);
    }

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        new Task.Backgroundable(project, "Ollamassist is starting ...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                project.getService(OllamaService.class).init();
                loadModelsAndNotify();
            }
        }.queue();
        return "EXECUTE";
    }

    private void loadModelsAndNotify() {
        AutocompleteService.get();

        ApplicationManager.getApplication()
                .getMessageBus()
                .syncPublisher(ModelAvailableNotifier.TOPIC)
                .onModelAvailable();
    }
}
