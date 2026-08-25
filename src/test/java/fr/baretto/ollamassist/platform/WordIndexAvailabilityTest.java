package fr.baretto.ollamassist.platform;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Probe: does JetBrains' word index answer inside a light test fixture?
 *
 * <p>The agent's discovery phase calls {@link PsiSearchHelper#processAllFilesWithWord} and
 * silently falls back to grep when it throws. A harness running on a fake project would
 * therefore exercise the fallback while reporting success, and would never measure the
 * discovery path the plugin actually takes. This test pins which substrate the harness
 * can trust.
 */
public class WordIndexAvailabilityTest extends BasePlatformTestCase {


    public void testWordIndexFindsAWordDeclaredInAFixtureFile() {
        myFixture.addFileToProject("src/main/java/com/example/FizzBuzzService.java", """
                package com.example;

                public class FizzBuzzService {
                    public String convert(int value) {
                        return value % 15 == 0 ? "FizzBuzz" : String.valueOf(value);
                    }
                }
                """);

        List<String> matched = new ArrayList<>();
        PsiSearchHelper.getInstance(getProject()).processAllFilesWithWord(
                "FizzBuzzService",
                GlobalSearchScope.projectScope(getProject()),
                psiFile -> {
                    matched.add(psiFile.getName());
                    return true;
                },
                false);

        assertEquals(List.of("FizzBuzzService.java"), matched);
    }

    /**
     * Pins the other half of the trade-off: a light fixture indexes its files but does not
     * write them to disk.
     *
     * <p>Every mutating tool calls {@code FilePathGuard.resolveConfined}, which resolves
     * symlinks with {@code toRealPath()} — a call that cannot succeed on a path this fixture
     * only holds in memory. Swapping in {@code TempDirTestFixtureImpl} inverts the problem
     * exactly: the file lands on disk and the word index then returns nothing, because it
     * falls outside the light project's content root.
     *
     * <p>So neither stock fixture supports both halves of the agent. An agent harness that
     * exercises discovery <em>and</em> the file tools needs a heavy fixture whose content
     * root is a real directory. This test exists so that constraint is discovered here
     * rather than halfway through building the harness.
     */
    public void testLightFixtureKeepsItsFilesOutOfTheRealFilesystem() {
        var psiFile = myFixture.addFileToProject("src/main/java/com/example/Billing.java",
                "package com.example;\n\npublic class Billing {\n}\n");

        Path path = Path.of(psiFile.getVirtualFile().getPath());

        assertFalse("light fixture unexpectedly wrote to disk: " + path, Files.exists(path));
    }
}
