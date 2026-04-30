package com.peter.nbagamemaster.app.unit;

import com.peter.nbagamemaster.app.NBAMasterApp;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NBAMasterAppUnitTest {

    @Test
    void doChatReturnsModelContent() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Mocked answer")))
        ));

        NBAMasterApp nbaMasterApp = new NBAMasterApp(chatModel);

        String answer = nbaMasterApp.doChat("Who wins tonight?", "session-1");

        assertEquals("Mocked answer", answer);
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
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage(structuredResponse)))
        ));

        NBAMasterApp nbaMasterApp = new NBAMasterApp(chatModel);

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
    }

}
