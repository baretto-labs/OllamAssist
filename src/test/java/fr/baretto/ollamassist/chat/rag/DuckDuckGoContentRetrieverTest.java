package fr.baretto.ollamassist.chat.rag;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.assertj.core.api.Assertions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DuckDuckGoContentRetrieverTest {

    private DuckDuckGoContentRetriever retriever;

    @BeforeEach
    void setUp() {
        retriever = new DuckDuckGoContentRetriever(10);
    }

    @Test
    void cleanText_shouldReturnPlainText() {
        String html = "<p>Hello <b>World</b></p>";
        String clean = DuckDuckGoContentRetriever.cleanText(html);
        assertEquals("Hello World", clean);
    }

    @Test
    void cleanText_shouldReturnEmptyOnNullOrBlank() {
        assertEquals("", DuckDuckGoContentRetriever.cleanText(null));
        assertEquals("", DuckDuckGoContentRetriever.cleanText("   "));
    }

    @Test
    void cleanUrl_shouldFixProtocol() {
        assertEquals("https://example.com", DuckDuckGoContentRetriever.cleanUrl("//example.com"));
        assertEquals("https://example.com", DuckDuckGoContentRetriever.cleanUrl("http://example.com"));
        assertEquals("https://example.com", DuckDuckGoContentRetriever.cleanUrl("https://example.com"));
    }

    @Test
    void isValidUrl_shouldReturnTrueOnlyForValidUrls() {
        assertTrue(DuckDuckGoContentRetriever.isValidUrl("https://example.com"));
        assertTrue(DuckDuckGoContentRetriever.isValidUrl("http://example.com"));
        assertTrue(DuckDuckGoContentRetriever.isValidUrl("//example.com"));
        assertFalse(DuckDuckGoContentRetriever.isValidUrl("ftp://example.com"));
        assertFalse(DuckDuckGoContentRetriever.isValidUrl(null));
        assertFalse(DuckDuckGoContentRetriever.isValidUrl(""));
        assertFalse(DuckDuckGoContentRetriever.isValidUrl("https://duckduckgo.com")); // without /l/
    }

    @Test
    void parseHtmlResults_shouldParseSimpleHtml() {
        String html = """
                <div class="web-result">
                  <h2><a href="https://example.com">Title</a></h2>
                  <div class="result__snippet">Snippet</div>
                </div>
                """;

        Document doc = Jsoup.parse(html);
        List<?> results = retriever.parseHtmlResults(doc);
        assertEquals(1, results.size());
    }

    @Test
    void parseApiResponse_shouldParseAbstractAndAnswer() {
        String json = """
                {
                  "Abstract": "Abstract text",
                  "AbstractURL": "https://abstract.com",
                  "Answer": "Answer text"
                }
                """;

        List<?> results = retriever.mapResponse(json);
        assertEquals(2, results.size());
    }

    @Test
    void parseApiResponse_shouldParseResultsNode() {
        String json = """
                {
                  "Results": [
                    { "Text": "Title1", "FirstURL": "https://url1.com" },
                    { "Text": "Title2", "FirstURL": "https://url2.com" }
                  ]
                }
                """;

        List<?> results = retriever.mapResponse(json);
        assertEquals(2, results.size());
    }

    @Test
    void parseApiResponse_shouldParseRelatedTopics() {
        String json = """
                {
                  "RelatedTopics": [
                    { "Text": "Topic1", "FirstURL": "https://t1.com" },
                    { "Topics": [
                      { "Text": "SubTopic1", "FirstURL": "https://st1.com" }
                    ]}
                  ]
                }
                """;

        List<?> results = retriever.mapResponse(json);
        assertEquals(1, results.size());
    }

    @Test
    void mapResponse_withEmptyJson_shouldReturnEmptyList() {
        List<?> results = retriever.mapResponse("{}");
        assertTrue(results.isEmpty());
    }



    void shouldRetrieveResultsFromDuckDuckGo() {
        DuckDuckGoContentRetriever duckDuckGoContentRetriever = new DuckDuckGoContentRetriever(3);

        List<Content> results = duckDuckGoContentRetriever.retrieve(new Query("OllamAssist"));

        Assertions.assertThat(results).isNotEmpty();

        results.forEach(content -> {
            Assertions.assertThat(content.textSegment()).isNotNull();
        });

        results.forEach(content -> System.out.println( content.textSegment()));
    }
}