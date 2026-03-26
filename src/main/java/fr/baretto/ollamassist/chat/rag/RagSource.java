package fr.baretto.ollamassist.chat.rag;

import dev.langchain4j.rag.content.Content;

import java.nio.file.Path;

/**
 * DTO representing a single RAG source chunk used to generate an AI response.
 *
 * <p>Built from the {@link Content} objects retrieved by {@link ContextRetriever} before
 * LangChain4j injects them into the LLM prompt. Carries just enough information for
 * the UI to display a human-readable source reference and navigate to the file.
 */
public record RagSource(
        String fileName,      // basename of the source file, e.g. "MyClass.java"
        String displayLabel,  // fqn, "line N", or a short description
        String absolutePath,  // full path for editor navigation; null for web sources
        SourceType sourceType
) {

    public enum SourceType { INDEX, WORKSPACE, WEB }

    /**
     * Builds a {@code RagSource} from a LangChain4j {@link Content} object.
     *
     * @param content    the retrieved content chunk
     * @param sourceType which retriever produced this chunk
     */
    public static RagSource fromContent(Content content, SourceType sourceType) {
        var meta = content.textSegment().metadata();

        // PSI chunks store the full path directly; fallback chunks store dir + name separately.
        String absolutePath = meta != null ? meta.getString(CodeAwareDocumentSplitter.META_SOURCE_FILE) : null;
        if (absolutePath == null && meta != null) {
            String dir  = meta.getString("absolute_directory_path");
            String name = meta.getString("file_name");
            if (dir != null && name != null) {
                absolutePath = dir + "/" + name;
            }
        }
        String fileName = absolutePath != null ? Path.of(absolutePath).getFileName().toString() : null;

        String displayLabel;
        if (sourceType == SourceType.WEB) {
            // Web chunks have no file metadata; use first line of text as label
            String text = content.textSegment().text();
            displayLabel = text != null && text.length() > 80 ? text.substring(0, 80) + "…" : text;
            fileName = "web";
        } else {
            String fqn = meta != null ? meta.getString(CodeAwareDocumentSplitter.META_FQN) : null;
            if (fqn != null && !fqn.isBlank()) {
                displayLabel = fqn;
            } else if (meta != null && meta.getString("line_start") != null) {
                displayLabel = (fileName != null ? fileName : "?") + " : line " + meta.getString("line_start");
            } else if (sourceType == SourceType.WORKSPACE) {
                displayLabel = "Current file";
                if (fileName == null) fileName = "workspace";
            } else {
                displayLabel = fileName != null ? fileName : "unknown";
            }
        }

        return new RagSource(
                fileName != null ? fileName : "unknown",
                displayLabel,
                absolutePath,
                sourceType
        );
    }
}
