package fr.baretto.ollamassist.chat.rag;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

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

    public static int getMaxFiles() {
        return OllamAssistSettings.getInstance().getIndexationSize();
    }

    public List<String> collectFilePaths() {
        VirtualFile baseDir = project.getBaseDir();
        return ReadAction.nonBlocking(() -> {
            AtomicInteger count = new AtomicInteger(0);
            List<String> sourceFiles = new ArrayList<>(getMaxFiles());
            List<String> otherFiles = new ArrayList<>(getMaxFiles());

            VfsUtilCore.visitChildrenRecursively(baseDir, new VirtualFileVisitor<>() {
                @Override
                public boolean visitFile(@NotNull VirtualFile file) {
                    if (count.get() >= getMaxFiles()) {
                        return false;
                    }

                    if (shouldSkipFile(file)) {
                        return true;
                    }

                    if (shouldProcessFile(file)) {
                        addFileToProperList(file, sourceFiles, otherFiles, count);
                    }

                    return true;
                }
            });

            return mergeAndLimitResults(sourceFiles, otherFiles);
        }).executeSynchronously();
    }

    private boolean shouldSkipFile(VirtualFile file) {
        return file.isDirectory() && shouldExcludedDirectory(file);
    }

    private boolean shouldProcessFile(VirtualFile file) {
        return !file.isDirectory() &&
                (fileIndex.isInSource(file) || shouldBeIndexed(file));
    }

    private void addFileToProperList(VirtualFile file,
                                     List<String> sources,
                                     List<String> others,
                                     AtomicInteger counter) {
        if (fileIndex.isInSource(file)) {
            if (sources.size() < getMaxFiles()) {
                sources.add(file.getPath());
                counter.incrementAndGet();
            }
        } else {
            if (counter.get() < getMaxFiles()) {
                others.add(file.getPath());
                counter.incrementAndGet();
            }
        }
    }

    private List<String> mergeAndLimitResults(List<String> sources, List<String> others) {
        List<String> result = Stream.concat(sources.stream(), others.stream())
                .limit(getMaxFiles())
                .toList();

        if (result.size() >= getMaxFiles()) {
            notifyLimitReached();
        }

        return result;
    }

    private void notifyLimitReached() {
        project.getMessageBus().syncPublisher(Notifications.TOPIC)
                .notify(new Notification(
                        "RAG_Indexation",
                        "Limit reached",
                        "Maximum indexable files limit (" + getMaxFiles() + ") exceeded. Editing or creating files will trigger their indexing.",
                        NotificationType.WARNING
                ));
    }

    private boolean shouldExcludedDirectory(@NotNull VirtualFile file) {
        return fileIndex.isExcluded(file)
                || file.getPath().startsWith(".")
                || fileIndex.isUnderIgnored(file)
                || isIgnoredByGit(file);
    }

    public boolean isIgnoredByGit(@NotNull VirtualFile file) {
        try {
            return ChangeListManager.getInstance(project).isIgnoredFile(file);
        } catch (Exception e) {
            log.warn("Git verification error ignored for {}", file.getPath(), e);
            return true;
        }
    }

    public boolean shouldBeIndexed(@NotNull VirtualFile file) {
        return file.isValid() &&
                file.getLength() > 0 &&
                !fileIndex.isExcluded(file) &&
                !file.getFileType().isBinary() &&
                shouldBeIndexed.matches(Path.of(file.getPath()));
    }
}