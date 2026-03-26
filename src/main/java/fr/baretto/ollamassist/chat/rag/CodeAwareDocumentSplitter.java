package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class CodeAwareDocumentSplitter implements DocumentSplitter {

    private static final int FALLBACK_CHUNK_LINES = 60;
    private static final int FALLBACK_OVERLAP_LINES = 10;
    private static final int MAX_CHUNK_CHARS = 3000;

    static final String CHUNK_TYPE_CLASS = "class";
    static final String CHUNK_TYPE_METHOD = "method";
    static final String CHUNK_TYPE_OTHER = "other";
    static final String META_FQN = "fqn";
    static final String META_CHUNK_TYPE = "chunk_type";
    static final String META_SOURCE_FILE = "source_file";

    private final Project project;

    public CodeAwareDocumentSplitter(Project project) {
        this.project = project;
    }

    @Override
    public List<TextSegment> split(Document document) {
        String absoluteDir = document.metadata().getString("absolute_directory_path");
        String fileName = document.metadata().getString("file_name");

        if (absoluteDir == null || fileName == null) {
            return fallbackSplit(document.text(), document.metadata());
        }

        String filePath = absoluteDir + "/" + fileName;

        if (fileName.endsWith(".java")) {
            return splitJavaFile(filePath, document.text(), document.metadata());
        }

        return fallbackSplit(document.text(), document.metadata());
    }

    private List<TextSegment> splitJavaFile(String filePath, String rawText, Metadata docMetadata) {
        try {
            return ReadAction.compute(() -> {
                VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(filePath);
                if (vf == null) {
                    log.debug("VirtualFile not found for {}, using fallback", filePath);
                    return fallbackSplit(rawText, docMetadata);
                }
                PsiFile psiFile = PsiManager.getInstance(project).findFile(vf);
                if (!(psiFile instanceof PsiJavaFile javaFile)) {
                    return fallbackSplit(rawText, docMetadata);
                }
                List<TextSegment> chunks = extractJavaChunks(javaFile, filePath, docMetadata);
                return chunks.isEmpty() ? fallbackSplit(rawText, docMetadata) : chunks;
            });
        } catch (Throwable e) {
            log.warn("PSI chunking failed for {}, using fallback: {}", filePath, e.getMessage());
            return fallbackSplit(rawText, docMetadata);
        }
    }

    private List<TextSegment> extractJavaChunks(PsiJavaFile javaFile, String filePath, Metadata docMetadata) {
        List<TextSegment> chunks = new ArrayList<>();

        for (PsiClass psiClass : javaFile.getClasses()) {
            String fqn = psiClass.getQualifiedName();
            if (fqn == null) continue;

            chunks.add(buildClassChunk(psiClass, fqn, filePath, docMetadata));

            for (PsiMethod method : psiClass.getMethods()) {
                chunks.add(buildMethodChunk(method, fqn, filePath, docMetadata));
            }
        }

        return chunks;
    }

    private TextSegment buildClassChunk(PsiClass psiClass, String fqn, String filePath, Metadata docMetadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("// ").append(fqn).append("\n");

        String classText = psiClass.getText();
        int bodyStart = classText.indexOf('{');
        if (bodyStart > 0) {
            sb.append(classText, 0, bodyStart).append("{\n");
        } else {
            sb.append(psiClass.getName()).append(" {\n");
        }

        for (PsiField field : psiClass.getFields()) {
            sb.append("  ").append(field.getText().trim()).append("\n");
        }

        for (PsiMethod method : psiClass.getMethods()) {
            sb.append("  ").append(buildMethodSignature(method)).append("\n");
        }
        sb.append("}");

        Metadata metadata = copyMetadata(docMetadata);
        metadata.put(META_FQN, fqn);
        metadata.put(META_CHUNK_TYPE, CHUNK_TYPE_CLASS);
        metadata.put(META_SOURCE_FILE, filePath);

        return TextSegment.from(truncate(sb.toString()), metadata);
    }

    private TextSegment buildMethodChunk(PsiMethod method, String classFqn, String filePath, Metadata docMetadata) {
        String fqn = classFqn + "#" + method.getName();

        StringBuilder sb = new StringBuilder();
        sb.append("// ").append(fqn).append("\n");
        sb.append(method.getText());

        Metadata metadata = copyMetadata(docMetadata);
        metadata.put(META_FQN, fqn);
        metadata.put(META_CHUNK_TYPE, CHUNK_TYPE_METHOD);
        metadata.put(META_SOURCE_FILE, filePath);

        return TextSegment.from(truncate(sb.toString()), metadata);
    }

    private String buildMethodSignature(PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        String text = method.getText().trim();
        if (body != null) {
            String bodyText = body.getText();
            int idx = text.lastIndexOf(bodyText);
            if (idx > 0) {
                return text.substring(0, idx).trim() + ";";
            }
        }
        return text;
    }

    List<TextSegment> fallbackSplit(String text, Metadata docMetadata) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] lines = text.split("\n", -1);
        List<TextSegment> chunks = new ArrayList<>();
        int i = 0;

        while (i < lines.length) {
            int end = Math.min(i + FALLBACK_CHUNK_LINES, lines.length);
            String chunk = String.join("\n", Arrays.copyOfRange(lines, i, end));
            if (!chunk.isBlank()) {
                Metadata metadata = copyMetadata(docMetadata);
                metadata.put(META_CHUNK_TYPE, CHUNK_TYPE_OTHER);
                metadata.put("line_start", String.valueOf(i + 1));
                chunks.add(TextSegment.from(truncate(chunk), metadata));
            }
            i += FALLBACK_CHUNK_LINES - FALLBACK_OVERLAP_LINES;
        }

        return chunks;
    }

    private Metadata copyMetadata(Metadata source) {
        Metadata copy = new Metadata();
        if (source != null) {
            source.toMap().forEach((k, v) -> copy.put(k, String.valueOf(v)));
        }
        return copy;
    }

    private String truncate(String text) {
        return text.length() > MAX_CHUNK_CHARS ? text.substring(0, MAX_CHUNK_CHARS) : text;
    }
}
