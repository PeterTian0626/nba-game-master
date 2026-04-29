package com.peter.nbagamemaster.app;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NBAMasterAppTest {

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

}
