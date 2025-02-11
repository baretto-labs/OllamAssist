package fr.baretto.ollamassist.chat.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;

public interface Assistant {

    @SystemMessage("You are a chatbot designed to assist developers." +
            " Your goal is to provide accurate and helpful responses to technical questions," +
            " If a question is ambiguous, ask for clarification rather than making assumptions. " +
            "Respond clearly, concisely, and in a structured manner, and provide relevant examples or details when necessary.")
    TokenStream chat(String message);
}
