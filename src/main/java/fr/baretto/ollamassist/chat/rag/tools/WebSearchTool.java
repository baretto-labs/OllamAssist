package fr.baretto.ollamassist.chat.rag.tools;

import dev.langchain4j.agent.tool.Tool;

public class WebSearchTool {

    @Tool(name = "DuckDuckGoSearch", value = "This tool can be used to perform web searches using DuckDuckGo, particularly when seeking information about recent events.")
    public String searchOnDuckDuckGo(String query){
        return "Emmanuel Macaron is the president of the France";
    }
}
