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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
public class FilesUtil {

    private static final String NOTIFICATION_GROUP = "RAG_Indexation";
    private static final String LIMIT_REACHED_TITLE = "Limit reached";
    private static final String LIMIT_REACHED_MESSAGE_FORMAT = "Maximum indexable files limit (%d) exceeded. Editing or creating files will trigger their indexing.";

    private final Project project;
    private final ProjectFileIndex fileIndex;
    private final ShouldBeIndexed shouldBeIndexed;
    @Getter
    private final int maxFiles;

    public FilesUtil(Project project) {
        this(project, ProjectFileIndex.getInstance(project), new ShouldBeIndexed(), OllamAssistSettings.getInstance().getIndexationSize());
    }

    public FilesUtil(Project project, ProjectFileIndex fileIndex, ShouldBeIndexed shouldBeIndexed, int maxFiles) {
        this.project = project;
        this.fileIndex = fileIndex;
        this.shouldBeIndexed = shouldBeIndexed;
        this.maxFiles = maxFiles;
    }

    public List<String> collectFilePaths() {
        return ReadAction.nonBlocking(this::collectFilePathsInternal).executeSynchronously();
    }

    List<String> collectFilePathsInternal() {
        VirtualFile baseDir = project.getBaseDir();
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
                    return false;
                }

                if (shouldProcessFile(file)) {
                    addFileToProperList(file, sourceFiles, otherFiles, count);
                }

                return true;
            }
        });

        return mergeAndLimitResults(sourceFiles, otherFiles);

    }

    boolean shouldSkipFile(VirtualFile file) {
        return file.isDirectory() && shouldExcludedDirectory(file);
    }

    boolean shouldProcessFile(VirtualFile file) {
        return !file.isDirectory() &&
                (fileIndex.isInSource(file) || shouldBeIndexed(file));
    }

    void addFileToProperList(VirtualFile file,
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
                        NOTIFICATION_GROUP,
                        LIMIT_REACHED_TITLE,
                        String.format(LIMIT_REACHED_MESSAGE_FORMAT, getMaxFiles()),
                        NotificationType.WARNING
                ));
    }

    boolean shouldExcludedDirectory(@NotNull VirtualFile file) {
        return fileIndex.isExcluded(file)
                || file.getName().startsWith(".")
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