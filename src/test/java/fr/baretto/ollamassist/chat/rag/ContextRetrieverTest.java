package fr.baretto.ollamassist.chat.rag;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import fr.baretto.ollamassist.setting.OllamAssistSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContextRetrieverTest {

    private ContentRetriever mainRetriever;
    private WorkspaceContextRetriever workspaceProvider;
    private DuckDuckGoContentRetriever webRetriever;
    private OllamAssistSettings settings;

    private ContextRetriever contextRetriever;

    @BeforeEach
    void setUp() {
        mainRetriever = mock(ContentRetriever.class);
        workspaceProvider = mock(WorkspaceContextRetriever.class);
        webRetriever = mock(DuckDuckGoContentRetriever.class);
        settings = mock(OllamAssistSettings.class);

        // Enable web search by default
        when(settings.webSearchEnabled()).thenReturn(true);

        // Use constructor that allows injecting mocks
        contextRetriever = new ContextRetriever(mainRetriever, workspaceProvider, webRetriever, settings);
    }

    @Test
    void testRetrieve_nominal() {
        Query query = new Query("test query");

        // Mock Content
        TextSegment segment = mock(TextSegment.class);
        when(segment.text()).thenReturn("This is a long enough content for testing.");

        Content content = mock(Content.class);
        when(content.textSegment()).thenReturn(segment);

        // Mock main retriever
        when(mainRetriever.retrieve(query)).thenReturn(List.of(content));

        List<Content> result = contextRetriever.retrieve(query);

        assertEquals(1, result.size());
        assertEquals("This is a long enough content for testing.", result.get(0).textSegment().text());
    }

    @Test
    void testRetrieve_timeout() {
        Query query = new Query("slow query");

        when(mainRetriever.retrieve(query)).thenAnswer(invocation -> {
            Thread.sleep(3000); // simulate delay > 2s
            return List.of();
        });

        try (MockedStatic<Notifications.Bus> notifications = mockStatic(Notifications.Bus.class)) {
            List<Content> result = contextRetriever.retrieve(query);

            assertTrue(result.isEmpty());
            notifications.verify(() -> Notifications.Bus.notify(any(Notification.class)));
        } catch (Exception e) {
            fail("Should not throw exception");
        }
    }

    @Test
    void testRetrieve_internalServerException() {
        Query query = new Query("internal error");

        when(mainRetriever.retrieve(query)).thenThrow(new InternalServerException("embedding failed"));

        try (MockedStatic<Notifications.Bus> notifications = mockStatic(Notifications.Bus.class)) {
            // Capture la notification
            notifications.when(() -> Notifications.Bus.notify(any(Notification.class))).thenAnswer(invocation -> null);

            List<Content> result = contextRetriever.retrieve(query);

            assertTrue(result.isEmpty());

        }
    }

    @Test
    void testRetrieve_genericException() {
        Query query = new Query("generic error");

        when(mainRetriever.retrieve(query)).thenThrow(new RuntimeException("something went wrong"));

        try (MockedStatic<Notifications.Bus> notifications = mockStatic(Notifications.Bus.class)) {
            List<Content> result = contextRetriever.retrieve(query);

            assertTrue(result.isEmpty());
            notifications.verify(() -> Notifications.Bus.notify(argThat(n ->
                    n.getContent().contains("unexpected error"))));
        }
    }

    @Test
    void testRetrieve_webSearchDisabled() {
        Query query = new Query("test query");

        TextSegment segment = mock(TextSegment.class);
        when(segment.text()).thenReturn("This is content for webSearchDisabled test.");

        Content content = mock(Content.class);
        when(content.textSegment()).thenReturn(segment);

        when(mainRetriever.retrieve(query)).thenReturn(List.of(content));

        List<Content> result = contextRetriever.retrieve(query);

        assertEquals(1, result.size());
        assertEquals("This is content for webSearchDisabled test.", result.get(0).textSegment().text());
    }
}