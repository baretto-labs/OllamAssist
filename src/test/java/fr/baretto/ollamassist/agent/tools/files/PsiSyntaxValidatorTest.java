package fr.baretto.ollamassist.agent.tools.files;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PsiSyntaxValidator} factory methods and contracts.
 *
 * The production {@link PsiSyntaxValidator#forProject} implementation requires a running
 * IntelliJ Platform and is tested via integration tests. These tests cover the contracts
 * and test helpers used throughout the agent test suite.
 */
class PsiSyntaxValidatorTest {

    // -------------------------------------------------------------------------
    // alwaysValid
    // -------------------------------------------------------------------------

    @Test
    void alwaysValid_anyJavaContent_returnsEmpty() {
        PsiSyntaxValidator v = PsiSyntaxValidator.alwaysValid();
        assertThat(v.validate("Foo.java", "public class Foo {}")).isEmpty();
    }

    @Test
    void alwaysValid_invalidContent_stillReturnsEmpty() {
        PsiSyntaxValidator v = PsiSyntaxValidator.alwaysValid();
        assertThat(v.validate("Foo.java", "@GetMapping\npublic boolean x() { return true; }")).isEmpty();
    }

    @Test
    void alwaysValid_emptyContent_returnsEmpty() {
        PsiSyntaxValidator v = PsiSyntaxValidator.alwaysValid();
        assertThat(v.validate("Foo.java", "")).isEmpty();
    }

    @Test
    void alwaysValid_unknownExtension_returnsEmpty() {
        PsiSyntaxValidator v = PsiSyntaxValidator.alwaysValid();
        assertThat(v.validate("config.toml", "key = value")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // alwaysError
    // -------------------------------------------------------------------------

    @Test
    void alwaysError_returnsProvidedMessage() {
        PsiSyntaxValidator v = PsiSyntaxValidator.alwaysError("method outside class body");
        Optional<String> result = v.validate("PingController.java", "some content");
        assertThat(result).contains("method outside class body");
    }

    @Test
    void alwaysError_differentFileNames_sameBehavior() {
        PsiSyntaxValidator v = PsiSyntaxValidator.alwaysError("unexpected token");
        assertThat(v.validate("Foo.java", "x")).contains("unexpected token");
        assertThat(v.validate("bar.kt", "x")).contains("unexpected token");
        assertThat(v.validate("baz.xml", "x")).contains("unexpected token");
    }

    // -------------------------------------------------------------------------
    // Contract: validate() must never return null
    // -------------------------------------------------------------------------

    @Test
    void alwaysValid_returnValueIsNonNull() {
        assertThat(PsiSyntaxValidator.alwaysValid().validate("Foo.java", "content")).isNotNull();
    }

    @Test
    void alwaysError_returnValueIsNonNull() {
        assertThat(PsiSyntaxValidator.alwaysError("err").validate("Foo.java", "content")).isNotNull();
    }

    // -------------------------------------------------------------------------
    // forProject — headless behaviour (no IntelliJ runtime in unit tests)
    // The implementation must fail-open when PSI infrastructure is unavailable.
    // -------------------------------------------------------------------------

    @Test
    void forProject_withNullProject_failsOpen() {
        // In a headless test environment PsiFileFactory.getInstance(null) throws.
        // The validator must catch the exception and return Optional.empty() (fail-open).
        PsiSyntaxValidator v = PsiSyntaxValidator.forProject(null);
        Optional<String> result = v.validate("Foo.java", "public class Foo {}");
        // Must not throw, must return a non-null Optional
        assertThat(result).isNotNull();
        // In a headless context the PSI engine is unavailable → fail-open → empty
        assertThat(result).isEmpty();
    }
}
