package com.peter.nbagamemaster.app.unit;

import com.peter.nbagamemaster.app.NBAMasterApp;
import com.peter.nbagamemaster.rag.QueryRewriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NBAMasterAppUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {"How to bake a cake?", "What was the score of the latest Lakers game?",
            "How should a team evaluate a non-shooting guard?"})
    void doChatWithRagPreservesTheQuestionWhenNoRelevantDocumentsAreFound(String question) {
        ChatModel chatModel = mock(ChatModel.class);
        QueryRewriter queryRewriter = new QueryRewriter(chatModel, false, 0, 4000, 0, 0);
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Model response")))
        ));
        NBAMasterApp nbaMasterApp = new NBAMasterApp(chatModel, queryRewriter, 0.5);
        ReflectionTestUtils.setField(nbaMasterApp, "nbaAppVectorStore", vectorStore);

        assertEquals("Model response", nbaMasterApp.doChatWithRag(
                question, UUID.randomUUID().toString()));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getUserMessage().getText())
                .contains(question)
                .doesNotContain("I couldn't find relevant information in the NBA knowledge base.",
                        "Respond with exactly this message");
        assertThat(promptCaptor.getValue().getSystemMessage().getText())
                .contains("Politely decline requests unrelated to NBA basketball",
                        "When current facts cannot be verified", "Never claim to have used a tool");
    }

    @ParameterizedTest
    @ValueSource(strings = {"How to bake a cake?", "Evaluate a trade announced today for the Lakers."})
    void doChatWithRagUsesRetrievedDocumentsAsSupportingMaterial(String question) {
        ChatModel chatModel = mock(ChatModel.class);
        QueryRewriter queryRewriter = new QueryRewriter(chatModel, false, 0, 4000, 0, 0);
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("The Lakers need more shooting.")));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Model response")))
        ));
        NBAMasterApp nbaMasterApp = new NBAMasterApp(chatModel, queryRewriter, 0.5);
        ReflectionTestUtils.setField(nbaMasterApp, "nbaAppVectorStore", vectorStore);

        assertEquals("Model response", nbaMasterApp.doChatWithRag(
                question, UUID.randomUUID().toString()));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getUserMessage().getText())
                .contains(question, "The Lakers need more shooting.", "Supporting NBA reference material")
                .doesNotContain("using only the context", "not prior knowledge");
        assertThat(promptCaptor.getValue().getSystemMessage().getText())
                .contains("Politely decline requests unrelated to NBA basketball",
                        "Use available tools to verify current", "When current facts cannot be verified");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.8, 1.0})
    void doChatWithRagUsesTheConfiguredSimilarityThreshold(double threshold) {
        ChatModel chatModel = mock(ChatModel.class);
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Model response")))
        ));

        new ApplicationContextRunner()
                .withUserConfiguration(NBAMasterApp.class)
                .withBean(ChatModel.class, () -> chatModel)
                .withBean(VectorStore.class, () -> vectorStore)
                .withBean(QueryRewriter.class, () -> new QueryRewriter(chatModel, false, 0, 4000, 0, 0))
                .withPropertyValues("app.rag.retrieval.similarity-threshold=" + threshold)
                .run(context -> {
                    assertEquals("Model response", context.getBean(NBAMasterApp.class).doChatWithRag(
                            "How to bake a cake?", UUID.randomUUID().toString()));
                    ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
                    verify(vectorStore).similaritySearch(searchCaptor.capture());
                    assertEquals(threshold, searchCaptor.getValue().getSimilarityThreshold());
                });
    }

    @Test
    void doChatWithRagDoesNotDisguiseRetrievalFailuresAsMissingContext() {
        ChatModel chatModel = mock(ChatModel.class);
        QueryRewriter queryRewriter = new QueryRewriter(chatModel, false, 0, 4000, 0, 0);
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new IllegalStateException("Vector store unavailable"));
        NBAMasterApp nbaMasterApp = new NBAMasterApp(chatModel, queryRewriter, 0.5);
        ReflectionTestUtils.setField(nbaMasterApp, "nbaAppVectorStore", vectorStore);

        RuntimeException failure = assertThrows(RuntimeException.class, () -> nbaMasterApp.doChatWithRag(
                "Why did the Lakers trade for Westbrook?", UUID.randomUUID().toString()));

        assertThat(failure).hasStackTraceContaining("Vector store unavailable");
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void doChatWithRagSearchesRewrittenQueryAndAnswersOriginalQuestion() {
        ChatModel chatModel = mock(ChatModel.class);
        QueryRewriter queryRewriter = mock(QueryRewriter.class);
        VectorStore vectorStore = mock(VectorStore.class);
        String sessionId = UUID.randomUUID().toString();
        String question = "Why was the Lakers trade for Westbrook in 2021 considered bad?";
        String searchQuery = "Lakers Westbrook 2021 trade spacing depth contracts";
        when(queryRewriter.rewriteAndSave(sessionId, question)).thenReturn(searchQuery);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("The trade reduced shooting and depth.")));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("The Lakers lost shooting and depth.")))
        ));
        NBAMasterApp nbaMasterApp = new NBAMasterApp(chatModel, queryRewriter, 0.5);
        ReflectionTestUtils.setField(nbaMasterApp, "nbaAppVectorStore", vectorStore);

        assertEquals("The Lakers lost shooting and depth.", nbaMasterApp.doChatWithRag(question, sessionId));

        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(searchCaptor.capture());
        assertEquals(searchQuery, searchCaptor.getValue().getQuery());
        assertEquals(0.5, searchCaptor.getValue().getSimilarityThreshold());
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getUserMessage().getText())
                .contains(question, "The trade reduced shooting and depth.")
                .doesNotContain(searchQuery);
        verify(queryRewriter).rewriteAndSave(sessionId, question);
    }

    @Test
    void doChatReturnsModelContent() {
        ChatModel chatModel = mock(ChatModel.class);
        QueryRewriter queryRewriter = mock(QueryRewriter.class);
        when(queryRewriter.rewriteAndSave(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Mocked answer")))
        ));

        NBAMasterApp nbaMasterApp = new NBAMasterApp(chatModel, queryRewriter, 0.5);

        String answer = nbaMasterApp.doChat("Who wins tonight?", "session-1");

        assertEquals("Mocked answer", answer);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getSystemMessage().getText())
                .contains("Politely decline requests unrelated to NBA basketball",
                        "When current facts cannot be verified");
    }

    @Test
    void doChatWithTeamTradeAnalysisReturnsStructuredEvaluation() {
        String structuredResponse = """
                {
                  "teamName": "Los Angeles Lakers",
                  "assetsReceived": ["Dejounte Murray"],
                  "assetsSent": ["Austin Reaves", "2029 first-round pick"],
                  "shortTermImpact": "Adds another on-ball creator and improves perimeter defense.",
                  "longTermImpact": "Raises the ceiling now but reduces future flexibility.",
                  "basketballFit": "Murray fits as a secondary initiator next to LeBron and can defend point guards.",
                  "capOrContractImpact": "Murray is on a sizable multiyear deal that tightens cap flexibility.",
                  "mainRisks": ["Loss of depth", "Pick downside", "Shot consistency"],
                  "teamGrade": "B"
                }
                """;

        ChatModel chatModel = mock(ChatModel.class);
        QueryRewriter queryRewriter = mock(QueryRewriter.class);
        when(queryRewriter.rewriteAndSave(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage(structuredResponse)))
        ));

        NBAMasterApp nbaMasterApp = new NBAMasterApp(chatModel, queryRewriter, 0.5);

        NBAMasterApp.TeamTradeEvaluation evaluation = nbaMasterApp.doChatWithTeamTradeAnalysis(
                "Evaluate a Lakers trade for Dejounte Murray from the Lakers perspective.",
                "session-2"
        );

        assertAll(
                () -> assertEquals("Los Angeles Lakers", evaluation.teamName()),
                () -> assertEquals(List.of("Dejounte Murray"), evaluation.assetsReceived()),
                () -> assertEquals(List.of("Austin Reaves", "2029 first-round pick"), evaluation.assetsSent()),
                () -> assertEquals("Adds another on-ball creator and improves perimeter defense.",
                        evaluation.shortTermImpact()),
                () -> assertEquals("Raises the ceiling now but reduces future flexibility.",
                        evaluation.longTermImpact()),
                () -> assertEquals(
                        "Murray fits as a secondary initiator next to LeBron and can defend point guards.",
                        evaluation.basketballFit()
                ),
                () -> assertEquals("Murray is on a sizable multiyear deal that tightens cap flexibility.",
                        evaluation.capOrContractImpact()),
                () -> assertEquals(List.of("Loss of depth", "Pick downside", "Shot consistency"),
                        evaluation.mainRisks()),
                () -> assertEquals("B", evaluation.teamGrade())
        );

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getSystemMessage().getText())
                .contains("Politely decline requests unrelated to NBA basketball",
                        "When current facts cannot be verified", "TeamTradeEvaluation(");
    }

}
