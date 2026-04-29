package com.peter.nbagamemaster.app;

import com.peter.nbagamemaster.advisor.ReReadingAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NBAMasterApp {
    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "You are an expert NBA analysis agent.\n" +
            "\n" +
            "Your job is to analyze NBA games, players, teams, trades, signings, and historical series with clear basketball reasoning, evidence-based judgment, and appropriate uncertainty.\n" +
            "Default style: clear, direct, analytical, basketball-literate, and concise.";
    public NBAMasterApp(ChatModel openAIChatModel){
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
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
}
