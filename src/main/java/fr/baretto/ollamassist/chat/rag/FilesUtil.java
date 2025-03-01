package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class FilesUtil {
    private final Project project;
    private final ProjectFileIndex fileIndex;
    private final ShouldBeIndexed shouldBeIndexed;
    public FilesUtil(Project project) {
        this.project = project;
        this.fileIndex = ProjectFileIndex.getInstance(project);
        this.shouldBeIndexed = new ShouldBeIndexed();
    }

    public List<String> collectFilePaths() {
        VirtualFile baseDir = project.getBaseDir();
        return ReadAction.nonBlocking(() -> {
            List<String> paths = new ArrayList<>();
            VfsUtilCore.visitChildrenRecursively(baseDir, new VirtualFileVisitor<>() {
                @Override
                public boolean visitFile(@NotNull VirtualFile file) {
                    if (shouldBeIndexed(file)) {
                        paths.add(file.getPath());
                    }
                    return true;
                }
            });
            return paths;
        }).executeSynchronously();
    }

    public boolean shouldBeIndexed(@NotNull VirtualFile file) {
        return !file.isDirectory() &&
                file.isValid() &&
                file.getLength() > 0 &&
                !fileIndex.isExcluded(file) &&
                !file.getFileType().isBinary() &&
                shouldBeIndexed.matches(Path.of(file.getPath()));
    }
}