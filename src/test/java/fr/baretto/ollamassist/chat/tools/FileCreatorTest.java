package fr.baretto.ollamassist.chat.tools;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import fr.baretto.ollamassist.events.FileApprovalNotifier;
import fr.baretto.ollamassist.events.StopStreamingNotifier;
import fr.baretto.ollamassist.setting.ActionsSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileCreatorTest {

    @TempDir
    Path tempDir;

    private Project mockProject;
    private MessageBus mockMessageBus;
    private FileApprovalNotifier mockApprovalPublisher;
    private StopStreamingNotifier mockStopStreamingPublisher;
    private ActionsSettings mockSettings;
    private FileCreator fileCreator;

    @BeforeEach
    void setup() {
        mockProject = mock(Project.class);
        mockMessageBus = mock(MessageBus.class);
        mockApprovalPublisher = mock(FileApprovalNotifier.class);
        mockStopStreamingPublisher = mock(StopStreamingNotifier.class);
        mockSettings = mock(ActionsSettings.class);

        when(mockProject.getBasePath()).thenReturn(tempDir.toString());
        when(mockProject.getMessageBus()).thenReturn(mockMessageBus);
        when(mockMessageBus.syncPublisher(FileApprovalNotifier.TOPIC)).thenReturn(mockApprovalPublisher);
        when(mockMessageBus.syncPublisher(StopStreamingNotifier.TOPIC)).thenReturn(mockStopStreamingPublisher);

        fileCreator = new FileCreator(mockProject);
    }

    @Test
    void testCreateFile_withEmptyPath_returnsError() {
        // Given
        String emptyPath = "";
        String content = "test content";

        // When
        String result = fileCreator.createFile(emptyPath, content);

        // Then
        assertEquals("Error: File path cannot be empty", result);
        verifyNoInteractions(mockApprovalPublisher);
        verifyNoInteractions(mockStopStreamingPublisher);
    }

    @Test
    void testCreateFile_withNullPath_returnsError() {
        // Given
        String nullPath = null;
        String content = "test content";

        // When
        String result = fileCreator.createFile(nullPath, content);

        // Then
        assertEquals("Error: File path cannot be empty", result);
    }

    @Test
    void testCreateFile_outsideProjectDirectory_returnsError() {
        // Given
        String outsidePath = "../../outside/file.txt";
        String content = "test content";

        // When
        String result = fileCreator.createFile(outsidePath, content);

        // Then
        assertTrue(result.contains("Error"), "Expected error message but got: " + result);
        assertTrue(result.contains("File path must be within the project directory"),
                   "Expected error about file path but got: " + result);
    }

    @Test
    void testCreateFile_fileAlreadyExists_returnsError() throws IOException {
        // Given
        String filePath = "existing.txt";
        Path existingFile = tempDir.resolve(filePath);
        Files.createFile(existingFile);

        String content = "test content";

        try (MockedStatic<LocalFileSystem> mockedLFS = mockStatic(LocalFileSystem.class)) {
            LocalFileSystem mockLFS = mock(LocalFileSystem.class);
            VirtualFile mockVirtualFile = mock(VirtualFile.class);

            mockedLFS.when(LocalFileSystem::getInstance).thenReturn(mockLFS);
            when(mockLFS.findFileByPath(anyString())).thenReturn(mockVirtualFile);
            when(mockVirtualFile.exists()).thenReturn(true);

            // When
            String result = fileCreator.createFile(filePath, content);

            // Then
            assertTrue(result.startsWith("Error: File already exists"));
        }
    }

    @Test
    void testCreateFile_withAutoApproval_createsFileDirectly() {
        // Given
        String filePath = "test.txt";
        String content = "Hello World";

        try (MockedStatic<ActionsSettings> mockedSettings = mockStatic(ActionsSettings.class);
             MockedStatic<WriteAction> mockedWriteAction = mockStatic(WriteAction.class);
             MockedStatic<LocalFileSystem> mockedLFS = mockStatic(LocalFileSystem.class)) {

            mockedSettings.when(ActionsSettings::getInstance).thenReturn(mockSettings);
            when(mockSettings.isAutoApproveFileCreation()).thenReturn(true);

            LocalFileSystem mockLFS = mock(LocalFileSystem.class);
            mockedLFS.when(LocalFileSystem::getInstance).thenReturn(mockLFS);
            when(mockLFS.findFileByPath(anyString())).thenReturn(null);

            VirtualFile mockParentDir = mock(VirtualFile.class);
            when(mockLFS.findFileByPath(tempDir.toString())).thenReturn(mockParentDir);

            mockedWriteAction.when(() -> WriteAction.computeAndWait(any())).thenAnswer(invocation -> {
                // Simulate file creation
                Path newFile = tempDir.resolve(filePath);
                Files.writeString(newFile, content);
                return "File created successfully: " + filePath;
            });

            // When
            String result = fileCreator.createFile(filePath, content);

            // Then
            assertTrue(result.contains("File created successfully"));

            // Verify auto-creation notification was sent
            ArgumentCaptor<FileApprovalNotifier.ApprovalRequest> captor =
                ArgumentCaptor.forClass(FileApprovalNotifier.ApprovalRequest.class);
            verify(mockApprovalPublisher).requestApproval(captor.capture());

            FileApprovalNotifier.ApprovalRequest request = captor.getValue();
            assertEquals("File Created Automatically", request.getTitle());
            assertEquals(filePath, request.getFilePath());
            assertEquals(content, request.getContent());

            // Verify streaming was stopped
            verify(mockStopStreamingPublisher).stopStreaming();
        }
    }

    @Test
    void testCreateFile_withManualApproval_approved_createsFile(){
        // Given
        String filePath = "manual.txt";
        String content = "Manual approval content";

        try (MockedStatic<ApplicationManager> mockedAppManager = mockStatic(ApplicationManager.class);
             MockedStatic<ActionsSettings> mockedSettings = mockStatic(ActionsSettings.class);
             MockedStatic<WriteAction> mockedWriteAction = mockStatic(WriteAction.class);
             MockedStatic<LocalFileSystem> mockedLFS = mockStatic(LocalFileSystem.class)) {

            Application mockApp = mock(Application.class);
            mockedAppManager.when(ApplicationManager::getApplication).thenReturn(mockApp);

            mockedSettings.when(ActionsSettings::getInstance).thenReturn(mockSettings);
            when(mockSettings.isAutoApproveFileCreation()).thenReturn(false);

            LocalFileSystem mockLFS = mock(LocalFileSystem.class);
            mockedLFS.when(LocalFileSystem::getInstance).thenReturn(mockLFS);
            when(mockLFS.findFileByPath(anyString())).thenReturn(null);

            mockedWriteAction.when(() -> WriteAction.computeAndWait(any())).thenAnswer(invocation -> {
                Path newFile = tempDir.resolve(filePath);
                Files.writeString(newFile, content);
                return "File created successfully: " + filePath;
            });

            // Simulate user approval - complete immediately when requested
            doAnswer(invocation -> {
                FileApprovalNotifier.ApprovalRequest request = invocation.getArgument(0);
                request.getResponseFuture().complete(true); // User approves immediately
                return null;
            }).when(mockApprovalPublisher).requestApproval(any());

            // When
            String result = fileCreator.createFile(filePath, content);

            // Then
            assertTrue(result.contains("File created successfully"));

            // Verify approval request was sent
            ArgumentCaptor<FileApprovalNotifier.ApprovalRequest> captor =
                ArgumentCaptor.forClass(FileApprovalNotifier.ApprovalRequest.class);
            verify(mockApprovalPublisher).requestApproval(captor.capture());

            FileApprovalNotifier.ApprovalRequest request = captor.getValue();
            assertEquals("File Creation Request", request.getTitle());
            assertEquals(filePath, request.getFilePath());

            // Verify streaming was stopped
            verify(mockStopStreamingPublisher).stopStreaming();
        }
    }

    @Test
    void testCreateFile_withManualApproval_rejected_doesNotCreateFile() {
        // Given
        String filePath = "rejected.txt";
        String content = "This should not be created";

        try (MockedStatic<ApplicationManager> mockedAppManager = mockStatic(ApplicationManager.class);
             MockedStatic<ActionsSettings> mockedSettings = mockStatic(ActionsSettings.class);
             MockedStatic<LocalFileSystem> mockedLFS = mockStatic(LocalFileSystem.class)) {

            Application mockApp = mock(Application.class);
            mockedAppManager.when(ApplicationManager::getApplication).thenReturn(mockApp);

            mockedSettings.when(ActionsSettings::getInstance).thenReturn(mockSettings);
            when(mockSettings.isAutoApproveFileCreation()).thenReturn(false);

            LocalFileSystem mockLFS = mock(LocalFileSystem.class);
            mockedLFS.when(LocalFileSystem::getInstance).thenReturn(mockLFS);
            when(mockLFS.findFileByPath(anyString())).thenReturn(null);

            // Simulate user rejection - reject immediately when requested
            doAnswer(invocation -> {
                FileApprovalNotifier.ApprovalRequest request = invocation.getArgument(0);
                request.getResponseFuture().complete(false); // User rejects immediately
                return null;
            }).when(mockApprovalPublisher).requestApproval(any());

            // When
            String result = fileCreator.createFile(filePath, content);

            // Then
            assertEquals("File creation cancelled by user", result);

            // Verify file was NOT created
            assertFalse(Files.exists(tempDir.resolve(filePath)));

            // Verify streaming was stopped
            verify(mockStopStreamingPublisher).stopStreaming();
        }
    }

    @Test
    void testCreateFile_withNullContent_createsEmptyFile() {
        // Given
        String filePath = "empty.txt";
        String content = null;

        try (MockedStatic<ActionsSettings> mockedSettings = mockStatic(ActionsSettings.class);
             MockedStatic<WriteAction> mockedWriteAction = mockStatic(WriteAction.class);
             MockedStatic<LocalFileSystem> mockedLFS = mockStatic(LocalFileSystem.class)) {

            mockedSettings.when(ActionsSettings::getInstance).thenReturn(mockSettings);
            when(mockSettings.isAutoApproveFileCreation()).thenReturn(true);

            LocalFileSystem mockLFS = mock(LocalFileSystem.class);
            mockedLFS.when(LocalFileSystem::getInstance).thenReturn(mockLFS);
            when(mockLFS.findFileByPath(anyString())).thenReturn(null);

            mockedWriteAction.when(() -> WriteAction.computeAndWait(any())).thenAnswer(invocation -> {
                Path newFile = tempDir.resolve(filePath);
                Files.writeString(newFile, "");
                return "File created successfully: " + filePath;
            });

            // When
            String result = fileCreator.createFile(filePath, content);

            // Then
            assertTrue(result.contains("File created successfully"));

            // Verify empty content was used
            ArgumentCaptor<FileApprovalNotifier.ApprovalRequest> captor =
                ArgumentCaptor.forClass(FileApprovalNotifier.ApprovalRequest.class);
            verify(mockApprovalPublisher).requestApproval(captor.capture());
            assertEquals("", captor.getValue().getContent());
        }
    }

    @Test
    void testCreateFile_stopStreamingAlwaysCalled() {
        // Given
        String filePath = "test.txt";
        String content = "test";

        try (MockedStatic<ApplicationManager> mockedAppManager = mockStatic(ApplicationManager.class);
             MockedStatic<ActionsSettings> mockedSettings = mockStatic(ActionsSettings.class);
             MockedStatic<WriteAction> mockedWriteAction = mockStatic(WriteAction.class);
             MockedStatic<LocalFileSystem> mockedLFS = mockStatic(LocalFileSystem.class)) {

            Application mockApp = mock(Application.class);
            mockedAppManager.when(ApplicationManager::getApplication).thenReturn(mockApp);

            mockedSettings.when(ActionsSettings::getInstance).thenReturn(mockSettings);
            when(mockSettings.isAutoApproveFileCreation()).thenReturn(false);

            LocalFileSystem mockLFS = mock(LocalFileSystem.class);
            mockedLFS.when(LocalFileSystem::getInstance).thenReturn(mockLFS);
            when(mockLFS.findFileByPath(anyString())).thenReturn(null);

            mockedWriteAction.when(() -> WriteAction.computeAndWait(any())).thenReturn("File created successfully");

            // Simulate approval
            doAnswer(invocation -> {
                FileApprovalNotifier.ApprovalRequest request = invocation.getArgument(0);
                request.getResponseFuture().complete(true);
                return null;
            }).when(mockApprovalPublisher).requestApproval(any());

            // When
            fileCreator.createFile(filePath, content);

            // Then - verify stopStreaming was called exactly once
            verify(mockStopStreamingPublisher, times(1)).stopStreaming();
        }
    }
}
