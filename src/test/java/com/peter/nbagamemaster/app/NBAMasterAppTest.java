package com.peter.nbagamemaster.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Tag("e2e")
@EnabledIfSystemProperty(named = "nba.e2e.enabled", matches = "true")
class NBAMasterAppTest {

    private static final String ANTHONY_DAVIS_TRADE_QUESTION =
            "Analyze the Anthony Davis trade for the Lakers.";

    @Resource
    private NBAMasterApp nbaMasterApp;

    @Test
    void doChatWithMemory() {
        String chatId = UUID.randomUUID().toString();
        String message = "Hello, I'm a lakers fan";
        String answer = nbaMasterApp.doChat(message, chatId);

        message = "do yo remember which team I like?";
        answer = nbaMasterApp.doChat(message, chatId);
    }

    @Test
    void doChatWithTeamTradeAnalysisReturnsAnthonyDavisTradeEvaluation() {
        NBAMasterApp.TeamTradeEvaluation evaluation = nbaMasterApp.doChatWithTeamTradeAnalysis(
                ANTHONY_DAVIS_TRADE_QUESTION,
                UUID.randomUUID().toString()
        );

        assertAll(
                () -> assertNotNull(evaluation),
                () -> assertNotNull(evaluation.teamName()),
                () -> assertTrue(evaluation.teamName().toLowerCase(Locale.ROOT).contains("laker")),
                () -> assertNotNull(evaluation.assetsReceived()),
                () -> assertFalse(evaluation.assetsReceived().isEmpty()),
                () -> assertTrue(evaluation.assetsReceived().stream()
                        .anyMatch(asset -> asset.toLowerCase(Locale.ROOT).contains("anthony")
                                || asset.toLowerCase(Locale.ROOT).contains("davis"))),
                () -> assertNotNull(evaluation.shortTermImpact()),
                () -> assertFalse(evaluation.shortTermImpact().isBlank()),
                () -> assertNotNull(evaluation.longTermImpact()),
                () -> assertFalse(evaluation.longTermImpact().isBlank()),
                () -> assertNotNull(evaluation.mainRisks()),
                () -> assertFalse(evaluation.mainRisks().isEmpty()),
                () -> assertNotNull(evaluation.teamGrade()),
                () -> assertFalse(evaluation.teamGrade().isBlank())
        );
    }

}
