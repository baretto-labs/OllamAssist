package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.project.Project;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.ArrayList;
import java.util.List;


public class ContextRetriever implements ContentRetriever {

    private final ContentRetriever contentRetriever;
    private final WorkspaceContextRetriever workspaceContextProvider;


    public ContextRetriever(ContentRetriever contentRetriever, Project project) {
        this.contentRetriever = contentRetriever;
        this.workspaceContextProvider = project.getService(WorkspaceContextRetriever.class);
    }


    @Override
    public List<Content> retrieve(Query query) {
        List<Content> results = new ArrayList<>(contentRetriever.retrieve(query));

        results.addAll(workspaceContextProvider.get()
                .stream()
                .filter(content ->
                        content != null
                                && isRelevant(content)
                                && !containsContent(results, content))
                .toList());

        return results;
    }

    private boolean isRelevant(Content content) {
        String text = content.textSegment().text();
        return text != null && text.length() > 30;
    }

    private boolean containsContent(List<Content> results, Content content) {
        return results.stream()
                .anyMatch(c -> c.textSegment().text().equals(content.textSegment().text()));
    }
}