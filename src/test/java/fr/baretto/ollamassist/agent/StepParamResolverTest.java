package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.agent.tools.StepParamResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepParamResolverTest {

    @Test
    void noPreviousOutput_withPlaceholder_throwsException() {
        Map<String, Object> params = Map.of("path", "{{prev_output_first_line}}");

        assertThatThrownBy(() -> StepParamResolver.resolve(params, null))
                .isInstanceOf(StepParamResolver.UnresolvablePlaceholderException.class)
                .hasMessageContaining("path");
    }

    @Test
    void blankPreviousOutput_withPlaceholder_throwsException() {
        Map<String, Object> params = Map.of("path", "{{prev_output_first_line}}");

        assertThatThrownBy(() -> StepParamResolver.resolve(params, "   "))
                .isInstanceOf(StepParamResolver.UnresolvablePlaceholderException.class);
    }

    @Test
    void noPlaceholder_returnsParamsUnchanged() {
        Map<String, Object> params = Map.of("path", "src/main/Foo.java");

        Map<String, Object> result = StepParamResolver.resolve(params, "src/other/Bar.java");

        assertThat(result).isSameAs(params);
    }

    @Test
    void prevOutputFirstLine_replacedWithFirstLine() {
        Map<String, Object> params = Map.of("path", "{{prev_output_first_line}}");
        String output = "src/main/java/Foo.java\nsrc/test/java/FooTest.java";

        Map<String, Object> result = StepParamResolver.resolve(params, output);

        assertThat(result.get("path")).isEqualTo("src/main/java/Foo.java");
    }

    @Test
    void prevOutput_replacedWithFullOutput() {
        Map<String, Object> params = Map.of("query", "{{prev_output}}");
        String output = "line1\nline2\nline3";

        Map<String, Object> result = StepParamResolver.resolve(params, output);

        assertThat(result.get("query")).isEqualTo("line1\nline2\nline3");
    }

    @Test
    void prevOutputFirstLine_skipsBlankLines() {
        Map<String, Object> params = Map.of("path", "{{prev_output_first_line}}");
        String output = "\n  \nsrc/main/Foo.java\nsrc/test/FooTest.java";

        Map<String, Object> result = StepParamResolver.resolve(params, output);

        assertThat(result.get("path")).isEqualTo("src/main/Foo.java");
    }

    @Test
    void multipleParams_onlyPlaceholderReplaced() {
        Map<String, Object> params = Map.of(
                "path", "{{prev_output_first_line}}",
                "search", "old code",
                "replace", "new code"
        );
        String output = "src/main/Bar.java";

        Map<String, Object> result = StepParamResolver.resolve(params, output);

        assertThat(result.get("path")).isEqualTo("src/main/Bar.java");
        assertThat(result.get("search")).isEqualTo("old code");
        assertThat(result.get("replace")).isEqualTo("new code");
    }

    @Test
    void nonStringParam_leftUntouched() {
        Map<String, Object> params = Map.of("topK", 5, "path", "{{prev_output_first_line}}");
        String output = "some/file.java";

        Map<String, Object> result = StepParamResolver.resolve(params, output);

        assertThat(result.get("topK")).isEqualTo(5);
        assertThat(result.get("path")).isEqualTo("some/file.java");
    }

    @Test
    void bothPlaceholders_inSameValue_bothReplaced() {
        Map<String, Object> params = Map.of("content", "first={{prev_output_first_line}} all={{prev_output}}");
        String output = "line1\nline2";

        Map<String, Object> result = StepParamResolver.resolve(params, output);

        // first_line replaced first, then full output
        assertThat(result.get("content")).isEqualTo("first=line1 all=line1\nline2");
    }

    @Test
    void emptyParams_returnsSameInstance() {
        Map<String, Object> params = Map.of();

        Map<String, Object> result = StepParamResolver.resolve(params, "some output");

        assertThat(result).isSameAs(params);
    }

    @Test
    void singleLineOutput_firstLineEqualsFullOutput() {
        Map<String, Object> params = Map.of("path", "{{prev_output_first_line}}");
        String output = "src/main/Single.java";

        Map<String, Object> result = StepParamResolver.resolve(params, output);

        assertThat(result.get("path")).isEqualTo("src/main/Single.java");
    }

    // --- Named variable ({{var.NAME}}) tests ---

    @Test
    void varPlaceholder_resolved_fromVariablesMap() {
        Map<String, Object> params = Map.of("path", "{{var.targetPath}}");
        Map<String, String> variables = Map.of("targetPath", "src/main/java/Foo.java");

        Map<String, Object> result = StepParamResolver.resolve(params, "", variables);

        assertThat(result.get("path")).isEqualTo("src/main/java/Foo.java");
    }

    @Test
    void varPlaceholder_unknownVariable_throwsException() {
        Map<String, Object> params = Map.of("path", "{{var.missingVar}}");
        Map<String, String> variables = Map.of("otherVar", "some/path.java");

        assertThatThrownBy(() -> StepParamResolver.resolve(params, "", variables))
                .isInstanceOf(StepParamResolver.UnresolvablePlaceholderException.class)
                .hasMessageContaining("missingVar")
                .hasMessageContaining("otherVar");
    }

    @Test
    void varPlaceholder_emptyVariablesMap_throwsException() {
        Map<String, Object> params = Map.of("path", "{{var.targetPath}}");

        assertThatThrownBy(() -> StepParamResolver.resolve(params, "", Map.of()))
                .isInstanceOf(StepParamResolver.UnresolvablePlaceholderException.class)
                .hasMessageContaining("targetPath");
    }

    @Test
    void varPlaceholder_multipleVarsInOneParam_allResolved() {
        Map<String, Object> params = Map.of("content", "file={{var.filePath}} method={{var.methodName}}");
        Map<String, String> variables = Map.of(
                "filePath", "src/Foo.java",
                "methodName", "doSomething"
        );

        Map<String, Object> result = StepParamResolver.resolve(params, "", variables);

        assertThat(result.get("content")).isEqualTo("file=src/Foo.java method=doSomething");
    }

    @Test
    void varPlaceholder_mixedWithPrevOutput_bothResolved() {
        Map<String, Object> params = Map.of(
                "path", "{{var.baseDir}}/{{prev_output_first_line}}"
        );
        Map<String, String> variables = Map.of("baseDir", "src/main/java");

        Map<String, Object> result = StepParamResolver.resolve(params, "Foo.java\nBar.java", variables);

        assertThat(result.get("path")).isEqualTo("src/main/java/Foo.java");
    }

    @Test
    void varPlaceholder_noPrevOutputNeeded_noException() {
        Map<String, Object> params = Map.of("path", "{{var.storedPath}}");
        Map<String, String> variables = Map.of("storedPath", "src/Service.java");

        // No prev_output placeholder → previous output being blank should not throw
        Map<String, Object> result = StepParamResolver.resolve(params, "", variables);

        assertThat(result.get("path")).isEqualTo("src/Service.java");
    }

    @Test
    void noVariablesParam_twoArgOverload_varPlaceholderThrows() {
        Map<String, Object> params = Map.of("path", "{{var.someVar}}");

        // The 2-arg overload passes an empty variables map — missing var must throw
        assertThatThrownBy(() -> StepParamResolver.resolve(params, "prev output"))
                .isInstanceOf(StepParamResolver.UnresolvablePlaceholderException.class)
                .hasMessageContaining("someVar");
    }
}
