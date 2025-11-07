package fr.baretto.ollamassist.agent.observability;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SourceReference class.
 */
class SourceReferenceTest {

    @Test
    void builder_shouldCreateSourceReferenceWithAllFields() {
        SourceReference ref = SourceReference.builder()
            .uri("/path/to/file.java")
            .type(SourceType.FILE)
            .lineStart(10)
            .lineEnd(20)
            .snippet("public void test() {}")
            .description("Test method")
            .relevanceScore(0.95)
            .sourceAgent("RAG_SEARCH")
            .timestamp(Instant.now())
            .build();

        assertThat(ref.getUri()).isEqualTo("/path/to/file.java");
        assertThat(ref.getType()).isEqualTo(SourceType.FILE);
        assertThat(ref.getLineStart()).isEqualTo(10);
        assertThat(ref.getLineEnd()).isEqualTo(20);
        assertThat(ref.getSnippet()).isEqualTo("public void test() {}");
        assertThat(ref.getDescription()).isEqualTo("Test method");
        assertThat(ref.getRelevanceScore()).isEqualTo(0.95);
        assertThat(ref.getSourceAgent()).isEqualTo("RAG_SEARCH");
        assertThat(ref.getTimestamp()).isNotNull();
    }

    @Test
    void getDisplayName_shouldReturnFileNameForFileType() {
        SourceReference ref = SourceReference.builder()
            .uri("/path/to/MyClass.java")
            .type(SourceType.FILE)
            .build();

        assertThat(ref.getDisplayName()).isEqualTo("MyClass.java");
    }

    @Test
    void getDisplayName_shouldReturnUriForNonFileType() {
        SourceReference ref = SourceReference.builder()
            .uri("https://example.com/doc")
            .type(SourceType.URL)
            .build();

        assertThat(ref.getDisplayName()).isEqualTo("https://example.com/doc");
    }

    @Test
    void getNavigationUrl_shouldIncludeLineNumberForFiles() {
        SourceReference ref = SourceReference.builder()
            .uri("/path/to/file.java")
            .type(SourceType.FILE)
            .lineStart(42)
            .build();

        assertThat(ref.getNavigationUrl()).isEqualTo("file:///path/to/file.java:42");
    }

    @Test
    void isNavigable_shouldReturnTrueForFileClassCommit() {
        assertThat(SourceReference.builder().type(SourceType.FILE).build().isNavigable())
            .isTrue();
        assertThat(SourceReference.builder().type(SourceType.CLASS).build().isNavigable())
            .isTrue();
        assertThat(SourceReference.builder().type(SourceType.COMMIT).build().isNavigable())
            .isTrue();
        assertThat(SourceReference.builder().type(SourceType.URL).build().isNavigable())
            .isFalse();
        assertThat(SourceReference.builder().type(SourceType.SNIPPET).build().isNavigable())
            .isFalse();
    }
}
