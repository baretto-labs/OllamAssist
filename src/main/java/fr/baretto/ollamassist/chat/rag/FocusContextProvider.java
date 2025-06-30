package fr.baretto.ollamassist.chat.rag;


import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;

public interface FocusContextProvider {

    Content get(Query query);

}
