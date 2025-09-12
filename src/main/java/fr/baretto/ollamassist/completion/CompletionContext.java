package fr.baretto.ollamassist.completion;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents comprehensive context information for code completion.
 * This includes immediate code context, project-wide patterns, and metadata
 * to enhance the quality of AI-generated code suggestions.
 */
@Builder
@Getter
public class CompletionContext {
    
    /**
     * The immediate code context around the cursor position.
     * This includes the current method, class context, or surrounding code block.
     */
    @NotNull
    private final String immediateContext;
    
    /**
     * Project-level context including imports, class signatures, and key fields.
     * Helps the AI understand the broader codebase structure and dependencies.
     */
    @Nullable
    private final String projectContext;
    
    /**
     * Similar code patterns from the project's indexed codebase.
     * Retrieved through semantic search to provide relevant examples.
     */
    @Nullable
    private final String similarPatterns;
    
    /**
     * File extension to determine the programming language and apply
     * language-specific completion rules.
     */
    @NotNull
    private final String fileExtension;
    
    /**
     * Current cursor offset in the document for precise positioning.
     */
    private final int cursorOffset;
    
    /**
     * Additional metadata about the current file and context.
     * May include class name, package, method signature, etc.
     */
    @Nullable
    private final FileMetadata fileMetadata;
    
    /**
     * Metadata about the current file context.
     */
    @Builder
    @Getter
    public static class FileMetadata {
        @Nullable
        private final String className;
        
        @Nullable
        private final String packageName;
        
        @Nullable
        private final String currentMethodName;
        
        @Nullable
        private final String currentMethodSignature;
        
        private final boolean insideMethod;
        private final boolean insideClass;
        private final boolean insideComment;
    }
    
    /**
     * Returns a formatted string representation of the context for debugging.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CompletionContext{\n");
        sb.append("  fileExtension='").append(fileExtension).append("'\n");
        sb.append("  cursorOffset=").append(cursorOffset).append("\n");
        sb.append("  immediateContext.length=").append(immediateContext.length()).append("\n");
        sb.append("  projectContext.length=").append(projectContext != null ? projectContext.length() : 0).append("\n");
        sb.append("  similarPatterns.length=").append(similarPatterns != null ? similarPatterns.length() : 0).append("\n");
        if (fileMetadata != null) {
            sb.append("  fileMetadata=").append(fileMetadata).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
}