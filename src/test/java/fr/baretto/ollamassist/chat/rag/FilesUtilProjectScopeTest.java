package fr.baretto.ollamassist.chat.rag;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * File change events come from the whole IDE, not from one project. A file is indexed into a
 * project's KnowledgeIndex only if it belongs to that project.
 */
class FilesUtilProjectScopeTest {

    private FilesUtil filesUtil;

    @BeforeEach
    void setUp() {
        Project project = mock(Project.class);
        VirtualFile baseDir = mock(VirtualFile.class);
        when(baseDir.getPath()).thenReturn("/work/my-project");
        when(project.getBaseDir()).thenReturn(baseDir);

        ShouldBeIndexed sources = mock(ShouldBeIndexed.class);
        when(sources.matches(any())).thenReturn(true);

        filesUtil = new FilesUtil(project, mock(ProjectFileIndex.class), sources, 10);
    }

    @Test
    void aFileOfTheProjectIsIndexed() {
        assertThat(filesUtil.shouldBeIndexed(textFile("/work/my-project/src/Foo.java"))).isTrue();
    }

    @Test
    void aFileOfAnotherProjectIsNotIndexed() {
        assertThat(filesUtil.shouldBeIndexed(textFile("/work/other-project/src/Foo.java"))).isFalse();
    }

    @Test
    void aFileWhosePathOnlyStartsLikeTheProjectIsNotIndexed() {
        assertThat(filesUtil.shouldBeIndexed(textFile("/work/my-project-backup/src/Foo.java"))).isFalse();
    }

    private static VirtualFile textFile(String path) {
        VirtualFile file = mock(VirtualFile.class);
        when(file.getPath()).thenReturn(path);
        when(file.isValid()).thenReturn(true);
        when(file.getLength()).thenReturn(100L);
        FileType type = mock(FileType.class);
        when(type.isBinary()).thenReturn(false);
        when(file.getFileType()).thenReturn(type);
        return file;
    }
}
