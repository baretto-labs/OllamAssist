package fr.baretto.ollamassist.completion;

import com.intellij.openapi.application.ApplicationManager;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import fr.baretto.ollamassist.auth.AuthenticationHelper;
import fr.baretto.ollamassist.setting.ModelListener;
import fr.baretto.ollamassist.setting.OllamAssistSettings;

import java.util.HashMap;
import java.util.Map;


public class LightModelAssistant {
    private static Service service;

    LightModelAssistant() {
        ApplicationManager.getApplication().getMessageBus()
                .connect()
                .subscribe(ModelListener.TOPIC, (ModelListener) LightModelAssistant::reloadModel);
    }

    public static LightModelAssistant.Service get() {
        if (service == null) {
            service = init();
        }
        return service;
    }

    private static void reloadModel() {
        service = init();
    }

    private static Service init() {
        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel
                .builder()
                .temperature(0.2)
                .topK(30)
                .topP(0.7)
                .baseUrl(OllamAssistSettings.getInstance().getCompletionOllamaUrl())
                .modelName(OllamAssistSettings.getInstance().getCompletionModelName())
                .timeout(OllamAssistSettings.getInstance().getTimeoutDuration());
        
        // Add authentication if configured
        if (AuthenticationHelper.isAuthenticationConfigured()) {
            Map<String, String> customHeaders = new HashMap<>();
            customHeaders.put("Authorization", "Basic " + AuthenticationHelper.createBasicAuthHeader());
            builder.customHeaders(customHeaders);
        }
        
        OllamaChatModel model = builder.build();

        return AiServices.builder(Service.class)
                .chatModel(model)
                .build();
    }


    public interface Service {
        @UserMessage("""
                You are an expert code completion assistant specialized in contextual, intelligent suggestions.
                
                **ANALYSIS PHASE:**
                1. **Language Context**: Based on file extension {{extension}}, apply language-specific patterns
                2. **Code Structure**: Analyze indentation, bracing style, naming conventions from the immediate context
                3. **Scope Context**: Determine if you're in class/method/block scope from the provided context
                4. **Intent Recognition**: Identify what the developer is likely trying to accomplish
                
                **COMPLETION RULES:**
                1. **Contextual Awareness**: Use provided project context and similar patterns to inform your completion
                2. **Minimal Precision**: Provide ONLY the immediate next logical continuation
                3. **Syntactic Correctness**: Ensure proper syntax, balanced braces, required semicolons
                4. **Consistent Style**: Match existing code style (spacing, naming, patterns)
                5. **No Repetition**: Never repeat any part of the provided context
                
                **CONTEXT SOURCES:**
                - **Immediate Context**: {{context}}
                {{#projectContext}}
                - **Project Context**: {{projectContext}}
                {{/projectContext}}
                {{#similarPatterns}}
                - **Similar Code Patterns**: {{similarPatterns}}
                {{/similarPatterns}}
                
                **OUTPUT FORMAT:**
                ```{{extension}}
                <your_completion_here>
                ```
                
                **EXAMPLES:**
                
                For Java method signature:
                Context: `public String getData(String id`
                Completion: 
                ```java
                ) {
                    return dataService.findById(id);
                }
                ```
                
                For variable declaration:
                Context: `List<User> users = `
                Completion:
                ```java
                userService.getAllUsers();
                ```
                
                For conditional start:
                Context: `if (user != null && user.isActive()`
                Completion:
                ```java
                ) {
                    return user.getName();
                }
                ```
                
                Provide ONLY the completion that logically follows the context.
                """)
        String complete(@V("context") String context, 
                       @V("extension") String fileExtension,
                       @V("projectContext") String projectContext,
                       @V("similarPatterns") String similarPatterns);
        
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
        String completeBasic(@V("context") String context, @V("extension") String fileExtension);

        @SystemMessage("""
                You are an assistant specialized in generating precise and concise commit messages, 
                following the Conventional Commits specification.
                
                Your task is to analyze the output of a `git diff --staged` and write a concise and meaningful commit message.
                
                Rules:
                - Identify the nature of the change and choose the appropriate commit type: feat, fix, chore, docs, style, refactor, perf, test.
                - Optionally, use a scope if it makes the message clearer.
                - The subject line must be less than or equal to 80 characters.
                - Do not add any commentary, metadata, or explanation—return only the commit message.
                - Write in the specified locale language when provided.
                
                Stay precise, short, and aligned with the commit message conventions.
                """)
        @UserMessage("""                
                This is the staged `git diff`:
                {{gitDiff}}
                
                Write a one-line commit message that follows the Conventional Commits format:
                - Format: <type>(optional-scope): <short summary>
                - Types: feat, fix, chore, docs, style, refactor, perf, test.
                - Limit the subject line to 80 characters.
                
                Very important:
                - Your goal is to identify and summarize the **main feature or fix** that the diff is building towards — the most **structuring or functional** change for the **end user**.
                - This main change may involve multiple small technical steps (refactors, optimizations, display tweaks, etc.) — you must summarize **the purpose**, not the implementation.
                - Ignore which part has more lines of code. Focus only on **the final outcome for the user**.
                - If there’s no user-visible change, then describe the most meaningful internal improvement.
                
                Steps:
                1. Identify all distinct intentions in the code (feature, fix, refactor, doc, etc.).
                2. Determine which of these intentions is the main goal for the user.
                3. Write a single commit message that summarizes **that goal**.
                
                Examples:
                ❌ `refactor: clean diff logic` → (bad, if the goal was to enable auto commit message generation)
                ✅ `feat: add automatic commit message generation` → (good, reflects the purpose)
                
                Only return the final commit message. No explanation.
                
                                Example:
                                 feat: add user authentication
                """)
        String writecommitMessage(@V("gitDiff") String gitDiff);

        @SystemMessage("""
                You are an assistant specialized in **web search query generation**. \s
                Your task is to **transform a user input** into a **clear and concise web search query** of a few keywords or a short phrase.
                
                ### Guidelines:
                - Use **natural search keywords** that would work well in Google, Bing, or similar engines. \s
                - **Remove unnecessary words** or polite phrases (e.g., "please", "can you", "I need"). \s
                - If the query is vague, infer the **most likely intent** from the context. \s
                - Keep it **short and precise** (ideally **3–7 words**). \s
                - Do **not** add quotation marks or special syntax unless explicitly needed. \s
                
                ### Examples:
                **User:** "Can you tell me the weather in Paris tomorrow?" \s
                **Output:** `weather Paris tomorrow`
                
                **User:** "Explain LangChain4j CompletionRequest" \s
                **Output:** `LangChain4j CompletionRequest explanation`
                
                **User:** "How do I train a small Java LLM locally?" \s
                **Output:** `train small Java LLM locally`
                
                **User:** "Please summarize the latest news about Nvidia stocks" \s
                **Output:** `latest Nvidia stock news`
                """)
        @UserMessage("""
                **Output ONLY the query. Do NOT include notes, explanations, or extra text.**
                
                **User Prompt:**
                {{user_input}}
                """)
        String createWebSearchQuery(@V("user_input") String input);

    }
}
