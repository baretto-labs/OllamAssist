package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.project.Project;
import com.jgoodies.common.base.Strings;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class FilesUtil {

    private static final PathMatcher PATH_MATCHER = new ShouldBeIndexed();
    private static final int BATCH_SIZE = 100;
    private final Set<Path> excludedPaths;
    private final Project project;

    public FilesUtil(Project project){
        this.project = project;
        excludedPaths = Set.of(".git", ".idea",
                        "target",".gradle", "jars","build", "bin", "obj", "out", ".vs", "x64", "Debug", "Release", "CMakeFiles",
                "packages", ".nuget", "AppPackages", "Artifacts","vendor", "composer", "storage", ".phpstorm.meta.php",
                "bundle", "tmp", "log", ".ruby-version", ".rbenv", "pkg", "*.exe", "*.test", "coverage.txt","__pycache__",
                "venv", "env", "dist","*.egg-info","node_modules",".next", ".svelte-kit", ".parcel-cache","Cargo.lock",
                "gen", "captures", "local.properties","Pods", "DerivedData","*.xcworkspace", "*.xcodeproj")
                .stream().map(dir-> Path.of(project.getBasePath(), dir).toAbsolutePath())
                .collect(Collectors.toSet());
    }


    public void batch(Consumer<List<Document>> ingestor) {
        validateDirectory(project.getBasePath());
        Set<File> batch = new HashSet<>();

        try {
            Files.walkFileTree(Path.of(project.getBasePath()), getFileVisitor(ingestor, batch));
            if (!batch.isEmpty()) {
                processBatch(batch, project.getName(), ingestor);
                batch.clear();
            }
        } catch (IOException e) {
            handleIOException(e, "directory processing");
        }
    }

    private void processBatch(Set<File> files, String projectId, Consumer<List<Document>> ingestor) {
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


    private void validateDirectory(String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + directoryPath);
        }
    }

    private void handleIOException(IOException e, String context) {
        log.error("IOException during {}: {}", context, e.getMessage(), e);
        throw new RuntimeException(e);
    }

    public long count() {
        AtomicLong fileCount = new AtomicLong(0);
        try {
            Files.walkFileTree(Path.of(project.getBasePath()), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (excludedPaths.contains(dir.toAbsolutePath())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (PATH_MATCHER.matches(file) && attrs.isRegularFile() && attrs.size() > 0) {
                        fileCount.incrementAndGet();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            handleIOException(e, "file counting");
        }

        return fileCount.get();
    }

    private @NotNull SimpleFileVisitor<Path> getFileVisitor(Consumer<List<Document>> ingestor, Set<File> batch) {
        return new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (excludedPaths.contains(dir.toAbsolutePath())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (PATH_MATCHER.matches(file) && attrs.isRegularFile() && attrs.size() > 0) {
                    batch.add(file.toFile());
                    if (batch.size() >= BATCH_SIZE) {
                        processBatch(batch, project.getName(), ingestor);
                        batch.clear();
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        };
    }
}