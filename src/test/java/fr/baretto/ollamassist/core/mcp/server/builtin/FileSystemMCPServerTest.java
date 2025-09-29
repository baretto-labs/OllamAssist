package fr.baretto.ollamassist.core.mcp.server.builtin;

import fr.baretto.ollamassist.core.mcp.protocol.MCPResponse;
import fr.baretto.ollamassist.core.mcp.server.MCPServerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FileSystemMCPServer Tests")
class FileSystemMCPServerTest {

    @TempDir
    Path tempDir;

    private FileSystemMCPServer server;

    @BeforeEach
    void setUp() {
        server = new FileSystemMCPServer();
    }

    @Test
    @DisplayName("Should provide correct server configuration")
    void shouldProvideCorrectServerConfiguration() {
        // When
        MCPServerConfig config = server.getConfig();

        // Then
        assertThat(config.getId()).isEqualTo("filesystem");
        assertThat(config.getName()).isEqualTo("File System");
        assertThat(config.getType()).isEqualTo(MCPServerConfig.MCPServerType.BUILTIN);
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getCapabilities()).contains(
                "fs/read_file",
                "fs/write_file",
                "fs/list_directory",
                "fs/create_directory"
        );
    }

    @Test
    @DisplayName("Should write and read file successfully")
    void shouldWriteAndReadFileSuccessfully() throws IOException {
        // Given
        Path testFile = tempDir.resolve("test.txt");
        String content = "Hello, World!";

        // When - Write file
        MCPResponse writeResponse = server.executeCapability("fs/write_file", Map.of(
                "path", testFile.toString(),
                "content", content
        ));

        // Then
        assertThat(writeResponse.isSuccess()).isTrue();
        assertThat(Files.exists(testFile)).isTrue();

        // When - Read file
        MCPResponse readResponse = server.executeCapability("fs/read_file", Map.of(
                "path", testFile.toString()
        ));

        // Then
        assertThat(readResponse.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) readResponse.getResult();
        assertThat(result.get("content")).isEqualTo(content);
        assertThat(result.get("path")).isEqualTo(testFile.toString());
    }

    @Test
    @DisplayName("Should list directory contents")
    void shouldListDirectoryContents() throws IOException {
        // Given
        Files.createFile(tempDir.resolve("file1.txt"));
        Files.createFile(tempDir.resolve("file2.txt"));
        Files.createDirectory(tempDir.resolve("subdir"));

        // When
        MCPResponse response = server.executeCapability("fs/list_directory", Map.of(
                "path", tempDir.toString()
        ));

        // Then
        assertThat(response.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertThat(result.get("path")).isEqualTo(tempDir.toString());
        assertThat(result.get("count")).isEqualTo(3);
    }

    @Test
    @DisplayName("Should create directory")
    void shouldCreateDirectory() {
        // Given
        Path newDir = tempDir.resolve("newdir");

        // When
        MCPResponse response = server.executeCapability("fs/create_directory", Map.of(
                "path", newDir.toString()
        ));

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(Files.exists(newDir)).isTrue();
        assertThat(Files.isDirectory(newDir)).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertThat(result.get("created")).isEqualTo(true);
    }

    @Test
    @DisplayName("Should check file existence")
    void shouldCheckFileExistence() throws IOException {
        // Given
        Path existingFile = tempDir.resolve("existing.txt");
        Files.createFile(existingFile);
        Path nonExistingFile = tempDir.resolve("nonexisting.txt");

        // When - Check existing file
        MCPResponse existingResponse = server.executeCapability("fs/file_exists", Map.of(
                "path", existingFile.toString()
        ));

        // Then
        assertThat(existingResponse.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> existingResult = (Map<String, Object>) existingResponse.getResult();
        assertThat(existingResult.get("exists")).isEqualTo(true);
        assertThat(existingResult.get("type")).isEqualTo("file");

        // When - Check non-existing file
        MCPResponse nonExistingResponse = server.executeCapability("fs/file_exists", Map.of(
                "path", nonExistingFile.toString()
        ));

        // Then
        assertThat(nonExistingResponse.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> nonExistingResult = (Map<String, Object>) nonExistingResponse.getResult();
        assertThat(nonExistingResult.get("exists")).isEqualTo(false);
    }

    @Test
    @DisplayName("Should delete file")
    void shouldDeleteFile() throws IOException {
        // Given
        Path testFile = tempDir.resolve("todelete.txt");
        Files.createFile(testFile);
        assertThat(Files.exists(testFile)).isTrue();

        // When
        MCPResponse response = server.executeCapability("fs/delete_file", Map.of(
                "path", testFile.toString()
        ));

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(Files.exists(testFile)).isFalse();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertThat(result.get("existed")).isEqualTo(true);
        assertThat(result.get("deleted")).isEqualTo(true);
    }

    @Test
    @DisplayName("Should copy file")
    void shouldCopyFile() throws IOException {
        // Given
        Path sourceFile = tempDir.resolve("source.txt");
        Path targetFile = tempDir.resolve("target.txt");
        String content = "File content";
        Files.writeString(sourceFile, content);

        // When
        MCPResponse response = server.executeCapability("fs/copy_file", Map.of(
                "source", sourceFile.toString(),
                "target", targetFile.toString()
        ));

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(Files.exists(sourceFile)).isTrue(); // Source still exists
        assertThat(Files.exists(targetFile)).isTrue(); // Target created
        assertThat(Files.readString(targetFile)).isEqualTo(content);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertThat(result.get("source")).isEqualTo(sourceFile.toString());
        assertThat(result.get("target")).isEqualTo(targetFile.toString());
    }

    @Test
    @DisplayName("Should handle missing parameters")
    void shouldHandleMissingParameters() {
        // When - Missing path parameter
        MCPResponse response = server.executeCapability("fs/read_file", Map.of());

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().getMessage()).contains("Parameter 'path' is required");
    }

    @Test
    @DisplayName("Should handle file not found")
    void shouldHandleFileNotFound() {
        // When
        MCPResponse response = server.executeCapability("fs/read_file", Map.of(
                "path", "/nonexistent/file.txt"
        ));

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().getMessage()).contains("File does not exist");
    }

    @Test
    @DisplayName("Should handle unknown capability")
    void shouldHandleUnknownCapability() {
        // When
        MCPResponse response = server.executeCapability("unknown/capability", Map.of());

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError().getMessage()).contains("Capability not supported");
    }
}