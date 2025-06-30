package fr.baretto.ollamassist.chat.rag;


import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileUtilTest {

    private Project mockProject;
    private ProjectFileIndex mockFileIndex;
    private VirtualFile mockBaseDir;
    private FilesUtil filesUtil;
    private ShouldBeIndexed mockShouldBeIndexed = mock(ShouldBeIndexed.class);

    @BeforeEach
    void setup() {
        mockProject = mock(Project.class);
        mockFileIndex = mock(ProjectFileIndex.class);
        mockBaseDir = mock(VirtualFile.class);
        when(mockShouldBeIndexed.matches(any())).thenReturn(true);
        try (MockedStatic<ProjectFileIndex> mocked = mockStatic(ProjectFileIndex.class)) {
            mocked.when(() -> ProjectFileIndex.getInstance(mockProject)).thenReturn(mockFileIndex);

            when(mockProject.getBaseDir()).thenReturn(mockBaseDir);
            filesUtil = new FilesUtil(mockProject, mockFileIndex, mockShouldBeIndexed, 10);
        }
    }

    @Test
    void testMaxFilesIsReturnedFromSettings() {
        try (MockedStatic<OllamAssistSettings> mocked = mockStatic(OllamAssistSettings.class)) {
            OllamAssistSettings settings = mock(OllamAssistSettings.class);
            when(settings.getIndexationSize()).thenReturn(42);
            mocked.when(OllamAssistSettings::getInstance).thenReturn(settings);

            assertEquals(10, filesUtil.getMaxFiles());
        }
    }

    @Test
    void testShouldSkipFile_withExcludedDirectory() {
        VirtualFile dir = mock(VirtualFile.class);
        when(dir.isDirectory()).thenReturn(true);
        when(dir.isValid()).thenReturn(true);
        when(dir.getLength()).thenReturn(10L);
        FileType fileType = mock(FileType.class);
        when(fileType.isBinary()).thenReturn(false);
        when(dir.getFileType()).thenReturn(fileType);
        when(dir.getPath()).thenReturn("/tmp");
        when(mockFileIndex.isExcluded(dir)).thenReturn(false);

        FilesUtil util = new FilesUtil(mockProject, mockFileIndex, mockShouldBeIndexed, 10);
        assertTrue(util.shouldBeIndexed(dir));
    }

    @Test
    void testShouldProcessFile_validSource() {
        VirtualFile file = mock(VirtualFile.class);
        when(file.isDirectory()).thenReturn(false);
        when(mockFileIndex.isInSource(file)).thenReturn(true);

        assertTrue(filesUtil.shouldProcessFile(file));
    }

    @Test
    void testAddFileToProperList_addsToSources() {
        VirtualFile file = mock(VirtualFile.class);
        when(mockFileIndex.isInSource(file)).thenReturn(true);
        when(file.getPath()).thenReturn("/test/File.java");

        AtomicInteger counter = new AtomicInteger(0);
        List<String> sources = new java.util.ArrayList<>();
        List<String> others = new java.util.ArrayList<>();

        filesUtil.addFileToProperList(file, sources, others, counter);

        assertEquals(1, counter.get());
        assertTrue(sources.contains("/test/File.java"));
        assertTrue(others.isEmpty());
    }

    @Test
    void testAddFileToProperList_addsToOthers() {
        VirtualFile file = mock(VirtualFile.class);
        when(mockFileIndex.isInSource(file)).thenReturn(false);
        when(file.getPath()).thenReturn("/test/Other.txt");

        AtomicInteger counter = new AtomicInteger(0);
        List<String> sources = new java.util.ArrayList<>();
        List<String> others = new java.util.ArrayList<>();

        filesUtil.addFileToProperList(file, sources, others, counter);

        assertEquals(1, counter.get());
        assertTrue(others.contains("/test/Other.txt"));
        assertTrue(sources.isEmpty());
    }

    @Test
    void testShouldSkipFile_excludedDirectory() {
        VirtualFile dir = mock(VirtualFile.class);
        when(dir.isDirectory()).thenReturn(true);
        when(dir.getPath()).thenReturn("/project/target");

        when(mockFileIndex.isExcluded(dir)).thenReturn(true);
        when(mockFileIndex.isUnderIgnored(dir)).thenReturn(false); // facultatif
        when(dir.getName()).thenReturn("target");

        FilesUtil util = new FilesUtil(mockProject, mockFileIndex, mockShouldBeIndexed, 10);

        boolean result = util.shouldSkipFile(dir);

        assertTrue(result, "Le dossier 'target' doit être exclu");
    }

    @Test
    void testShouldSkipFile_fileIgnoredByGit() {
        VirtualFile file = mock(VirtualFile.class);
        when(file.isDirectory()).thenReturn(true);
        when(file.getPath()).thenReturn("/project/node_modules");
        when(file.getName()).thenReturn("node_modules");


        when(mockFileIndex.isExcluded(file)).thenReturn(false);
        when(mockFileIndex.isUnderIgnored(file)).thenReturn(false);

        try (MockedStatic<ChangeListManager> mockCLM = mockStatic(ChangeListManager.class)) {
            ChangeListManager clm = mock(ChangeListManager.class);
            when(clm.isIgnoredFile(file)).thenReturn(true);

            mockCLM.when(() -> ChangeListManager.getInstance(mockProject)).thenReturn(clm);

            FilesUtil util = new FilesUtil(mockProject, mockFileIndex, mockShouldBeIndexed, 10);

            boolean result = util.shouldSkipFile(file);
            assertTrue(result, "Le dossier ignoré par Git doit être exclu");
        }
    }

    @Test
    void testShouldExcludedDirectory_hiddenDirectory() {
        VirtualFile file = mock(VirtualFile.class);
        when(file.getPath()).thenReturn("/project/.idea");
        when(mockFileIndex.isExcluded(file)).thenReturn(false);
        when(mockFileIndex.isUnderIgnored(file)).thenReturn(false);
        when(file.getName()).thenReturn(".idea");

        try (MockedStatic<ChangeListManager> mockCLM = mockStatic(ChangeListManager.class)) {
            ChangeListManager clm = mock(ChangeListManager.class);
            when(clm.isIgnoredFile(file)).thenReturn(false);

            mockCLM.when(() -> ChangeListManager.getInstance(mockProject)).thenReturn(clm);

            FilesUtil util = new FilesUtil(mockProject, mockFileIndex, mockShouldBeIndexed, 10);
            boolean excluded = util.shouldExcludedDirectory(file);

            assertTrue(excluded, "Les dossiers cachés comme .idea doivent être exclus");
        }
    }

    @Test
    void testCollectFilePaths_shouldReturnLimitedIndexedFiles() {
        Project project = mock(Project.class);
        ProjectFileIndex fileIndex = mock(ProjectFileIndex.class);
        ShouldBeIndexed shouldBeIndexed = mock(ShouldBeIndexed.class);
        FilesUtil filesUtilForTests = new FilesUtil(project, fileIndex, shouldBeIndexed, 10);

        VirtualFile baseDir = mock(VirtualFile.class);
        when(project.getBaseDir()).thenReturn(baseDir);

        VirtualFile file1 = mockFile("/src/file1.java", false);
        VirtualFile file2 = mockFile("/src/file2.java", false);
        VirtualFile file3 = mockFile("/test/file3.test", false);
        VirtualFile file4 = mockFile("/readme.md", false);

        List<VirtualFile> allFiles = List.of(file1, file2, file3, file4);

        mockRecursiveTraversal(baseDir, allFiles);

        when(fileIndex.isInSource(file1)).thenReturn(true);
        when(fileIndex.isInSource(file2)).thenReturn(true);
        when(fileIndex.isInSource(file3)).thenReturn(false);
        when(fileIndex.isInSource(file4)).thenReturn(false);

        when(shouldBeIndexed.matches(any())).thenReturn(true);

        List<String> paths = filesUtilForTests.collectFilePathsInternal();

        assertEquals(4, paths.size());
        assertTrue(paths.contains("/src/file1.java"));
        assertTrue(paths.contains("/src/file2.java"));
        assertTrue(paths.contains("/test/file3.test"));
        assertTrue(paths.contains("/readme.md"));
    }

    private VirtualFile mockFile(String path, boolean isDirectory) {
        VirtualFile file = mock(VirtualFile.class);
        when(file.getPath()).thenReturn(path);
        when(file.isDirectory()).thenReturn(isDirectory);
        when(file.isValid()).thenReturn(true);
        when(file.getLength()).thenReturn(100L);
        when(file.getFileType()).thenReturn(mock(FileType.class));
        when(file.getFileType().isBinary()).thenReturn(false);
        return file;
    }

    private void mockRecursiveTraversal(VirtualFile baseDir, List<VirtualFile> files) {
        mockStatic(VfsUtilCore.class).when(() ->
                VfsUtilCore.visitChildrenRecursively(eq(baseDir), any())
        ).thenAnswer(invocation -> {
            VirtualFileVisitor<?> visitor = invocation.getArgument(1);
            for (VirtualFile file : files) {
                visitor.visitFile(file);
            }
            return null;
        });
    }

}