package fr.baretto.ollamassist.ai.autocomplete;

import com.intellij.openapi.application.ApplicationManager;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import fr.baretto.ollamassist.setting.SettingsListener;
import lombok.Getter;

public class AutoCompleteService {

    @Getter
    private Service service;

    AutoCompleteService() {
        this("http://localhost:11434", OllamAssistSettings.getInstance().getCompletionModelName(), 0.2, 30, 0.7);
        ApplicationManager.getApplication().getMessageBus()
                .connect()
                .subscribe(SettingsListener.TOPIC, (SettingsListener) this::reloadModel);

    }

    public AutoCompleteService(String url, String modelName, double temperature, int topK, double topP) {
        init(url, modelName, temperature, topK, topP);
    }

    private void reloadModel(OllamAssistSettings.State state) {
        init("http://localhost:11434", OllamAssistSettings.getInstance().getCompletionModelName(), 0.2, 30, 0.7);
    }

    private void init(String url, String modelName, double temperature, int topK, double topP) {
        OllamaChatModel model = OllamaChatModel
                .builder()
                .temperature(temperature)
                .topK(topK)
                .topP(topP)
                .baseUrl(url)
                .modelName(modelName)
                .build();

        service = AiServices.builder(Service.class)
                .chatLanguageModel(model)
                .build();
    }
}
