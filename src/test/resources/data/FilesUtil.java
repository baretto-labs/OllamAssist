package fr.baretto.ollamassist.ai.store;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import lombok.AccessLevel;
import lombok.Getter;
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

    private static final int BATCH_SIZE = 25;

    public static void batch(String projectId, String directoryPath, PathMatcher fileFilter, Consumer<List<Document>> ingestor) {
        validateDirectory(directoryPath);

        log.info("Starting ingestion for project {} in directory {}", projectId, directoryPath);
        List<File> batch = new ArrayList<>();

        try (var paths = Files.walk(Path.of(directoryPath))) {

            paths.filter(file -> fileFilter.matches(file.toAbsolutePath()))
                    .map(path -> {
                        System.err.println("PATH : "+ path);
                        return path;
                    }).map(Path::toFile)
                    .forEach(file -> {
                        batch.add(file);
                        if (batch.size() >= BATCH_SIZE) {
                            processBatch(batch, projectId, ingestor);
                        }
                    });

            if (!batch.isEmpty()) {
                processBatch(batch, projectId, ingestor);
            }
        } catch (IOException e) {
            handleIOException(e, "directory processing");
        }

        log.info("Ingestion completed for project {}", projectId);
    }

    private static void processBatch(List<File> batch, String projectId, Consumer<List<Document>> ingestor) {
        try (TempDirectory tempDir = new TempDirectory()) {
            copyFilesToTempDirectory(batch, tempDir.getPath());
            List<Document> documents = loadDocumentsWithMetadata(tempDir.getPath(), projectId);
            ingestor.accept(documents);
        } catch (IOException e) {
            handleIOException(e, "batch processing");
        } finally {
            batch.clear();
        }
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

    private static void copyFilesToTempDirectory(List<File> files, Path tempDir) throws IOException {
        for (File file : files) {
            Files.copy(file.toPath(), tempDir.resolve(file.getName()));
        }
    }

    private static List<Document> loadDocumentsWithMetadata(Path tempDir, String projectId) {
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(tempDir.toAbsolutePath().toString());
        documents.forEach(doc -> doc.metadata().put("project_id", projectId));
        return documents;
    }

    @Getter
    private static class TempDirectory implements AutoCloseable {
        private final Path path;

        private TempDirectory() throws IOException {
            path = Files.createTempDirectory("temp_batch_");
        }

        @Override
        public void close() throws IOException {
            try (var files = Files.walk(path)) {
                files.map(Path::toFile)
                        .forEach(File::delete);
            }
            path.toFile().delete();
        }
    }
}