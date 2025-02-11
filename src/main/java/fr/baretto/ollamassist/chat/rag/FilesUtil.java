package fr.baretto.ollamassist.chat.rag;

import com.jgoodies.common.base.Strings;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FilesUtil {

    private static final int BATCH_SIZE = 100;

    public static void batch(String projectId, String directoryPath, PathMatcher fileFilter, Consumer<List<Document>> ingestor) {
        validateDirectory(directoryPath);
        List<File> batch = new ArrayList<>();

        try (var paths = Files.walk(Path.of(directoryPath))) {
            paths.filter(path -> fileFilter.matches(path.toAbsolutePath()))
                    .map(Path::toFile)
                    .filter(file -> file.isFile() && file.length() > 0)
                    .forEach(file -> {
                        batch.add(file);
                        if (batch.size() >= BATCH_SIZE) {
                            processBatch(batch, projectId, ingestor);
                            batch.clear();
                        }
                    });

            if (!batch.isEmpty()) {
                processBatch(batch, projectId, ingestor);
                batch.clear();
            }
        } catch (IOException e) {
            handleIOException(e, "directory processing");
        }
    }

    private static void processBatch(List<File> files, String projectId, Consumer<List<Document>> ingestor) {
        List<Document> documents = files.stream()
                .map(file -> {
                    try {
                        return FileSystemDocumentLoader.loadDocument(file.toPath());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(doc -> doc != null &&Strings.isNotBlank(doc.toTextSegment().text()))
                .map(document -> {
                    document.metadata().put("project_id", projectId);
                    return document;
                })
                .toList();
        ingestor.accept(documents);
    }


    private static void validateDirectory(String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + directoryPath);
        }
    }

    private static void handleIOException(IOException e, String context) {
        log.error("IOException during {}: {}", context, e.getMessage(), e);
        throw new RuntimeException(e);
    }

}