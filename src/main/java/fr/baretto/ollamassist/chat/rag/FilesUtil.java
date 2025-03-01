package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class FilesUtil {
    private final Project project;
    private final ProjectFileIndex fileIndex;

    public FilesUtil(Project project) {
        this.project = project;
        this.fileIndex = ProjectFileIndex.getInstance(project);
    }

    public List<String> collectFilePaths() {
        VirtualFile baseDir = project.getBaseDir();
        return ReadAction.nonBlocking(() -> {
            List<String> paths = new ArrayList<>();
            VfsUtilCore.visitChildrenRecursively(baseDir, new VirtualFileVisitor<>() {
                @Override
                public boolean visitFile(@NotNull VirtualFile file) {
                    if (!file.isDirectory() &&
                            !fileIndex.isExcluded(file) &&
                            !file.getFileType().isBinary()) {
                        paths.add(file.getPath());
                    }
                    return true;
                }
            });
            return paths;
        }).executeSynchronously();
    }
}