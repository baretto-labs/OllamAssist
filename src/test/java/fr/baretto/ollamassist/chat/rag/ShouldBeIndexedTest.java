package fr.baretto.ollamassist.chat.rag;

import fr.baretto.ollamassist.setting.OllamAssistSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


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

            Assertions.assertTrue(new ShouldBeIndexedForTest().matches(Files.createFile(tempDir.resolve("srcHello.java"))));
            Assertions.assertTrue(new ShouldBeIndexedForTest().matches(Files.createFile(tempDir.resolve("pom.xml"))));
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
            excludedFiles = List.of(".git", ".json");
            includedFiles = List.of("src/", ".java", "pom.xml");
        }
    }
}