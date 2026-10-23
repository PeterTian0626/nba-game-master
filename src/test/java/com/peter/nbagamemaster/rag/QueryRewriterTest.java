package com.peter.nbagamemaster.rag;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryRewriterTest {

    @Test
    void rewriteAndSaveStoresRewrittenQueryBySession() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Lakers Westbrook trade impact 2021")))
        ));

        QueryRewriter queryRewriter = new QueryRewriter(chatModel, true, 0, 400, 1000, 32);

        String rewritten = queryRewriter.rewriteAndSave(
                "session-1",
                "Can you explain why the Lakers-Westbrook trade in 2021 is seen as negative?"
        );

        assertEquals("Lakers Westbrook trade impact 2021", rewritten);
        assertEquals(rewritten, queryRewriter.getLastRewrittenQuery("session-1"));
    }

    @Test
    void rewriteUsesCacheForRepeatedQueries() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Anthony Davis trade Lakers impact")))
        ));

        QueryRewriter queryRewriter = new QueryRewriter(chatModel, true, 0, 400, 1000, 32);

        String query = "Please evaluate the Anthony Davis trade for the Lakers.";
        String first = queryRewriter.rewrite(query);
        String second = queryRewriter.rewrite(query);

        assertNotNull(first);
        assertEquals(first, second);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void rewriteSkipsModelCallWhenDisabled() {
        ChatModel chatModel = mock(ChatModel.class);

        QueryRewriter queryRewriter = new QueryRewriter(chatModel, false, 0, 400, 1000, 32);

        String query = "How should we evaluate this trade?";
        String rewritten = queryRewriter.rewrite(query);

        assertEquals(query, rewritten);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void rewritePreservesInputInPromptAndReturnsOnlyTrimmedModelOutput() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response("  Lakers trade fit 2021\n"));
        QueryRewriter queryRewriter = new QueryRewriter(chatModel, true, 0, 400, 10, 10);
        String query = "Evaluate the Lakers trade in 2021 using {basketball fit}.";

        assertEquals("Lakers trade fit 2021", queryRewriter.doQueryRewrite(query));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String rewritePrompt = promptCaptor.getValue().getUserMessage().getText();
        assertTrue(rewritePrompt.contains(query));
        assertTrue(rewritePrompt.contains("Do not answer the question"));
    }

    @Test
    void rewriteFallsBackOnModelFailureAndRetriesNextTime() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("Model unavailable"))
                .thenReturn(response("Lakers trade evaluation"));
        QueryRewriter queryRewriter = new QueryRewriter(chatModel, true, 0, 400, 10, 10);
        String query = "Please evaluate the Lakers trade.";

        assertEquals(query, queryRewriter.rewriteAndSave("session-1", query));
        assertEquals(query, queryRewriter.getLastRewrittenQuery("session-1"));
        assertEquals("Lakers trade evaluation", queryRewriter.rewrite(query));
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void rewriteDoesNotCacheEmptyModelOutput() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response(" \n "))
                .thenReturn(response("Lakers trade evaluation"));
        QueryRewriter queryRewriter = new QueryRewriter(chatModel, true, 0, 400, 10, 10);
        String query = "  Evaluate the Lakers trade.  ";

        assertEquals(query, queryRewriter.rewrite(query));
        assertEquals("Lakers trade evaluation", queryRewriter.rewrite(query));
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void rewriteSkipsQueriesOutsideConfiguredLengthRange() {
        ChatModel chatModel = mock(ChatModel.class);
        QueryRewriter queryRewriter = new QueryRewriter(chatModel, true, 10, 20, 10, 10);
        String longQuery = "Evaluate every aspect of the Lakers trade.";

        assertEquals("Lakers", queryRewriter.rewrite("Lakers"));
        assertEquals(longQuery, queryRewriter.rewrite(longQuery));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void rewriteEvictsLeastRecentlyUsedQueries() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response("NBA trade evaluation"));
        QueryRewriter queryRewriter = new QueryRewriter(chatModel, true, 0, 400, 2, 10);

        queryRewriter.rewrite("Lakers trade");
        queryRewriter.rewrite("Celtics trade");
        queryRewriter.rewrite("Lakers trade");
        queryRewriter.rewrite("Warriors trade");
        queryRewriter.rewrite("Lakers trade");
        queryRewriter.rewrite("Celtics trade");

        verify(chatModel, times(4)).call(any(Prompt.class));
    }

    @Test
    void sessionQueriesAreIsolatedUpdatedAndBounded() {
        ChatModel chatModel = mock(ChatModel.class);
        QueryRewriter queryRewriter = new QueryRewriter(chatModel, false, 0, 400, 10, 2);

        assertNull(queryRewriter.getLastRewrittenQuery("unknown"));
        queryRewriter.rewriteAndSave("session-1", "Lakers trade");
        queryRewriter.rewriteAndSave("session-2", "Celtics trade");
        queryRewriter.rewriteAndSave("session-1", "Lakers contracts");
        assertEquals("Celtics trade", queryRewriter.getLastRewrittenQuery("session-2"));
        assertEquals("Lakers contracts", queryRewriter.getLastRewrittenQuery("session-1"));
        queryRewriter.rewriteAndSave("session-3", "Warriors trade");

        assertNull(queryRewriter.getLastRewrittenQuery("session-2"));
        assertEquals("Lakers contracts", queryRewriter.getLastRewrittenQuery("session-1"));
        assertEquals("Warriors trade", queryRewriter.getLastRewrittenQuery("session-3"));
    }

    @Test
    void zeroCapacityDisablesCachingAndSessionStorage() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response("Lakers trade evaluation"));
        QueryRewriter queryRewriter = new QueryRewriter(chatModel, true, 0, 400, 0, 0);

        queryRewriter.rewriteAndSave("session-1", "Lakers trade");
        queryRewriter.rewrite("Lakers trade");

        assertNull(queryRewriter.getLastRewrittenQuery("session-1"));
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void invalidInputIsRejectedBeforeCallingModel() {
        ChatModel chatModel = mock(ChatModel.class);
        QueryRewriter queryRewriter = new QueryRewriter(chatModel, true, 0, 400, 10, 10);

        assertThrows(IllegalArgumentException.class, () -> queryRewriter.rewrite(null));
        assertThrows(IllegalArgumentException.class, () -> queryRewriter.rewrite(" \n "));
        assertThrows(IllegalArgumentException.class, () -> queryRewriter.rewriteAndSave(" ", "Lakers trade"));
        assertThrows(IllegalArgumentException.class, () -> queryRewriter.getLastRewrittenQuery(null));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void invalidConfigurationIsRejected() {
        ChatModel chatModel = mock(ChatModel.class);

        assertThrows(IllegalArgumentException.class, () -> new QueryRewriter(null, true, 0, 400, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new QueryRewriter(chatModel, true, -1, 400, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new QueryRewriter(chatModel, true, 10, 5, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new QueryRewriter(chatModel, true, 0, 400, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> new QueryRewriter(chatModel, true, 0, 400, 10, -1));
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
