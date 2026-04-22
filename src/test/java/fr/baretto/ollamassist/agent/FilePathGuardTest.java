package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.agent.tools.files.FilePathGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilePathGuardTest {

    @TempDir
    Path projectRoot;

    // -------------------------------------------------------------------------
    // Happy path — relative paths resolved inside the project
    // -------------------------------------------------------------------------

    @Test
    void relativePath_resolvedUnderProjectRoot() {
        Path result = FilePathGuard.resolveConfined("src/Main.java", projectRoot.toString());
        Path root = projectRoot.normalize().toAbsolutePath();
        assertThat(result).startsWithRaw(root);
        assertThat(result.toString()).endsWith("Main.java");
    }

    @Test
    void nestedRelativePath_resolvedCorrectly() {
        Path result = FilePathGuard.resolveConfined("src/main/java/Foo.java", projectRoot.toString());
        Path root = projectRoot.normalize().toAbsolutePath();
        assertThat(result.toString()).endsWith("Foo.java");
        assertThat(result).startsWithRaw(root);
    }

    @Test
    void absolutePathInsideProject_allowed() {
        String inside = projectRoot.resolve("src/Bar.java").normalize().toAbsolutePath().toString();
        Path result = FilePathGuard.resolveConfined(inside, projectRoot.toString());
        Path root = projectRoot.normalize().toAbsolutePath();
        assertThat(result).startsWithRaw(root);
    }

    // -------------------------------------------------------------------------
    // Path traversal — must throw SecurityException
    // -------------------------------------------------------------------------

    @Test
    void dotDotEscape_isBlocked() {
        assertThatThrownBy(() ->
                FilePathGuard.resolveConfined("../../etc/passwd", projectRoot.toString()))
                .isInstanceOf(FilePathGuard.PathTraversalException.class)
                .hasMessageContaining("project root");
    }

    @Test
    void absolutePathOutsideProject_isBlocked() {
        assertThatThrownBy(() ->
                FilePathGuard.resolveConfined("/etc/passwd", projectRoot.toString()))
                .isInstanceOf(FilePathGuard.PathTraversalException.class);
    }

    @Test
    void absolutePathToSshKey_isBlocked() {
        assertThatThrownBy(() ->
                FilePathGuard.resolveConfined("/Users/root/.ssh/id_rsa", projectRoot.toString()))
                .isInstanceOf(FilePathGuard.PathTraversalException.class);
    }

    @Test
    void encodedTraversal_isBlocked() {
        // Normalized path still escapes root via repeated ../
        assertThatThrownBy(() ->
                FilePathGuard.resolveConfined("src/../../../etc/hosts", projectRoot.toString()))
                .isInstanceOf(FilePathGuard.PathTraversalException.class);
    }

    // -------------------------------------------------------------------------
    // Null / blank base path
    // -------------------------------------------------------------------------

    @Test
    void nullBasePath_throwsIllegalState() {
        assertThatThrownBy(() ->
                FilePathGuard.resolveConfined("src/Foo.java", (String) null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base path");
    }

    @Test
    void blankBasePath_throwsIllegalState() {
        assertThatThrownBy(() ->
                FilePathGuard.resolveConfined("src/Foo.java", "  "))
                .isInstanceOf(IllegalStateException.class);
    }

    // -------------------------------------------------------------------------
    // Null / blank path
    // -------------------------------------------------------------------------

    @Test
    void nullPath_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                FilePathGuard.resolveConfined((String) null, projectRoot.toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // Symlink escape — non-existent target but symlinked parent must be caught
    // -------------------------------------------------------------------------

    @Test
    void symlinkParentPointingOutside_newFileBlocked(@org.junit.jupiter.api.io.TempDir java.nio.file.Path externalDir)
            throws Exception {
        // Create a symlink inside the project pointing to an external directory
        java.nio.file.Path link = projectRoot.resolve("external_link");
        java.nio.file.Files.createSymbolicLink(link, externalDir);

        // The file inside the symlink does NOT exist yet — this is the write-path scenario.
        // Without the fix, FilePathGuard would skip toRealPath() (IOException) and allow the write.
        assertThatThrownBy(() ->
                FilePathGuard.resolveConfined("external_link/secret.txt", projectRoot.toString()))
                .isInstanceOf(FilePathGuard.PathTraversalException.class)
                .hasMessageContaining("project root");
    }

    @Test
    void symlinkInsideProject_existingFile_blocked(@org.junit.jupiter.api.io.TempDir java.nio.file.Path externalDir)
            throws Exception {
        // Existing symlink to a file outside — already covered by toRealPath() on existing files,
        // but this verifies the existing behaviour is still intact after the fix.
        java.nio.file.Path externalFile = externalDir.resolve("secret.txt");
        java.nio.file.Files.writeString(externalFile, "secret");
        java.nio.file.Path link = projectRoot.resolve("link_to_secret.txt");
        java.nio.file.Files.createSymbolicLink(link, externalFile);

        assertThatThrownBy(() ->
                FilePathGuard.resolveConfined("link_to_secret.txt", projectRoot.toString()))
                .isInstanceOf(FilePathGuard.PathTraversalException.class);
    }

    @Test
    void normalNonExistentFile_insideProject_allowed() {
        // A new file path that does not exist yet but is inside the project must still be allowed.
        Path result = FilePathGuard.resolveConfined("src/new/File.java", projectRoot.toString());
        assertThat(result).startsWithRaw(projectRoot.normalize().toAbsolutePath());
    }
}
