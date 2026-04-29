package com.peter.nbagamemaster.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ReReadingAdvisorTest {

    @Test
    void beforeAugmentsUserMessage() {
        ReReadingAdvisor advisor = new ReReadingAdvisor();
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt("How are the Celtics doing this year?"))
                .build();

        ChatClientRequest updatedRequest = advisor.before(request, mock(AdvisorChain.class));
        String updatedText = updatedRequest.prompt().getUserMessage().getText();

        assertAll(
                () -> assertTrue(updatedText.contains("How are the Celtics doing this year?")),
                () -> assertTrue(updatedText.contains("Read the question again: How are the Celtics doing this year?")),
                () -> assertEquals(request.prompt().getSystemMessage().getText(),
                        updatedRequest.prompt().getSystemMessage().getText())
        );
    }

}
