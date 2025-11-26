package fr.baretto.ollamassist.chat.service;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface Assistant {

    @SystemMessage("{{systemPrompt}}")
    TokenStream chat(@V("systemPrompt") String systemPrompt, @UserMessage String message);


    @UserMessage("{{refactorPrompt}}")
    TokenStream refactor(@V("refactorPrompt") String refactorPrompt, @V("code") String code, @V("language") String language);

}
