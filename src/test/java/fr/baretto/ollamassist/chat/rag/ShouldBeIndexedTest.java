package fr.baretto.ollamassist.chat.rag;

import fr.baretto.ollamassist.setting.OllamAssistSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;


class ShouldBeIndexedTest {

    @TempDir
    Path tempDir;

    private OllamAssistSettings settings = Mockito.mock(OllamAssistSettings.class);

    @Test
    void should_returns_true_if_path_is_in_included_files() throws IOException {

        try (MockedStatic<OllamAssistSettings> ollamAssistSettingsMocked = Mockito.mockStatic(OllamAssistSettings.class)) {
            ollamAssistSettingsMocked.
                    when(OllamAssistSettings::getInstance)
                    .thenReturn(settings);

            Mockito.doReturn("src,pom.xml").when(settings).getSources();


            Path hello = tempDir.resolve("srcHello.java");
            createAndAppend(hello,"hello");
            Assertions.assertTrue(new ShouldBeIndexedForTest().matches(hello));
            Path pom = tempDir.resolve("pom.xml");
            createAndAppend(pom, "<xml></xml>");
            Assertions.assertTrue(new ShouldBeIndexedForTest().matches(pom));
        }
    }

    @Test
    void should_returns_false_if_path_is_in_excluded_files() throws IOException {
        try (MockedStatic<OllamAssistSettings> ollamAssistSettingsMocked = Mockito.mockStatic(OllamAssistSettings.class)) {
            ollamAssistSettingsMocked.
                    when(OllamAssistSettings::getInstance)
                    .thenReturn(settings);

            Mockito.doReturn("src,pom.xml").when(settings).getSources();

            Assertions.assertFalse(new ShouldBeIndexedForTest().matches(Files.createFile(tempDir.resolve(".git"))));
            Assertions.assertFalse(new ShouldBeIndexedForTest().matches(Files.createFile(tempDir.resolve("tmp.json"))));
        }

    }

    private static class ShouldBeIndexedForTest extends ShouldBeIndexed {
        ShouldBeIndexedForTest() {
            includedPaths = Set.of("src/", ".java", "pom.xml");
        }
    }

    public void createAndAppend(Path path, String content) throws IOException {
        Files.writeString(
                path,
                content,
                StandardOpenOption.CREATE
        );
    }
}