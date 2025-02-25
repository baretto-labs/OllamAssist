package fr.baretto.ollamassist.ai;

import com.intellij.openapi.application.ApplicationManager;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import fr.baretto.ollamassist.setting.SettingsListener;


public class AutocompleteService {
    private static Service service;

    AutocompleteService() {
        ApplicationManager.getApplication().getMessageBus()
                .connect()
                .subscribe(SettingsListener.TOPIC, (SettingsListener) AutocompleteService::reloadModel);
    }

    public static AutocompleteService.Service get() {
        if (service == null) {
            service = init();
        }
        return service;
    }

    private static void reloadModel(OllamAssistSettings.State state) {
        service = init();
    }

    private static Service init() {
        OllamaChatModel model = OllamaChatModel
                .builder()
                .temperature(0.2)
                .topK(30)
                .topP(0.7)
                .baseUrl(OllamAssistSettings.getInstance().getOllamaUrl())
                .modelName(OllamAssistSettings.getInstance().getCompletionModelName())
                .timeout(OllamAssistSettings.getInstance().getTimeoutDuration())
                .build();

        return AiServices.builder(Service.class)
                .chatLanguageModel(model)
                .build();
    }


    public interface Service {
        @UserMessage("""  
                You are an expert software developer specializing in writing clean, concise, and accurate code.\s
                
                Your task is to provide the **next immediate continuation** of a given code snippet while adhering strictly to the following guidelines:
                
                ### **Guidelines:**
                1. **Syntactically Correct:** Ensure the code has proper syntax (e.g., balanced braces `{}`, proper indentation, required semicolons, etc.).
                2. **Minimal and Contextual:** Only provide the minimal lines needed to logically continue the snippet based on the provided context. Avoid completing an entire method or block unless necessary.
                3. **Strictly Code Only:** The response must only include valid code wrapped in triple backticks, formatted for the programming language identified by the `{{extension}}` extension.
                4. **No Repetition or Modification:** Do not repeat or modify any part of the provided context.
                5. **One Logical Completion:** Provide only a single, logical continuation or block — no alternatives, explanations, or comments.
                6. **Well-Formatted Output:** Ensure clean formatting, proper indentation, and no trailing spaces or unnecessary line breaks.
                
                ### **Context:**
                {{context}}
                
                ### **Instructions:**
                1. Continue the code **exactly where the context ends**.
                2. Provide only the **next logical lines** required to maintain syntax and logic.
                3. Ensure the completion is **ready to run** without requiring additional edits.
                
                ### **Output Format:**
                Wrap the code in triple backticks for easy copy-paste. The output should look like this:
                    ```language
                    <completed_code>
                    ```
                """)
        String complete(@V("context") String context, @V("extension") String fileExtension);
    }
}
