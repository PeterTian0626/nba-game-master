package com.peter.nbagamemaster.app;

import com.peter.nbagamemaster.advisor.MyLoggerAdvisor;
import com.peter.nbagamemaster.advisor.ReReadingAdvisor;
import com.peter.nbagamemaster.chatMemory.FileBasedChatMemoryRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class NBAMasterApp {
    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "You are an expert NBA analysis agent.\n" +
            "\n" +
            "Your job is to analyze NBA games, players, teams, trades, signings, and historical series with clear basketball reasoning, evidence-based judgment, and appropriate uncertainty.\n" +
            "Default style: clear, direct, analytical, basketball-literate, and concise.";
    public NBAMasterApp(ChatModel openAIChatModel){
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        ChatMemoryRepository chatMemoryRepository = new FileBasedChatMemoryRepository(fileDir);
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
        chatClient = ChatClient.builder(openAIChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new ReReadingAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    /**
     * Basic conversation
     * @param message
     * @param sessionId
     * @return
     */
    public String doChat(String message, String sessionId){
        ChatResponse chatResponse = this.chatClient
                .prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .chatResponse();

        String content =  chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    public record TeamTradeEvaluation(
            String teamName,
            List<String> assetsReceived,
            List<String> assetsSent,
            String shortTermImpact,
            String longTermImpact,
            String basketballFit,
            String capOrContractImpact,
            List<String> mainRisks,
            String teamGrade
    ) {}

    private final static String SYSTEM_PROMPT_TRADE_ANALYSIS = "When the user asks for a trade evaluation from one team’s perspective, return an evaluation tailored to this Java record:\n" +
            "\n" +
            "TeamTradeEvaluation(\n" +
            "  teamName,\n" +
            "  assetsReceived,\n" +
            "  assetsSent,\n" +
            "  shortTermImpact,\n" +
            "  longTermImpact,\n" +
            "  basketballFit,\n" +
            "  capOrContractImpact,\n" +
            "  mainRisks,\n" +
            "  teamGrade\n" +
            ")";


    /**
     * Structured output
     * @param message
     * @param sessionId
     * @return
     */
    public TeamTradeEvaluation doChatWithTeamTradeAnalysis(String message, String sessionId){
        TeamTradeEvaluation tradeEvaluation = this.chatClient
                .prompt()
                .system(SYSTEM_PROMPT + SYSTEM_PROMPT_TRADE_ANALYSIS)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .entity(TeamTradeEvaluation.class);
        log.info("content: {}", tradeEvaluation);
        return tradeEvaluation;
    }

    @Resource
    private VectorStore nbaAppVectorStore;

    /**
     * Chat with RAG
     * @param message
     * @param sessionId
     * @return
     */
    public String doChatWithRag(String message, String sessionId){
        ChatResponse chatResponse =  chatClient
                .prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .advisors(new MyLoggerAdvisor())
                .advisors(QuestionAnswerAdvisor.builder(nbaAppVectorStore).build())
                .call()
                .chatResponse();
        String content =  chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
