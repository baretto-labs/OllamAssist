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
}
