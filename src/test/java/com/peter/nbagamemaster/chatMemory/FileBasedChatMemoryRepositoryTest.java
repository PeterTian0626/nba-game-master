package com.peter.nbagamemaster.chatMemory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileBasedChatMemoryRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAllPersistsMessagesAcrossRepositoryInstances() {
        FileBasedChatMemoryRepository repository = new FileBasedChatMemoryRepository(tempDir.toString());
        String conversationId = "session/alpha:42";
        List<Message> messages = List.of(
                new UserMessage("Who won the 2016 Finals?"),
                new AssistantMessage("The Cavaliers won in seven games.")
        );

        repository.saveAll(conversationId, messages);

        FileBasedChatMemoryRepository reloadedRepository = new FileBasedChatMemoryRepository(tempDir.toString());

        assertEquals(messages, reloadedRepository.findByConversationId(conversationId));
        assertEquals(List.of(conversationId), reloadedRepository.findConversationIds());
    }

    @Test
    void deleteByConversationIdRemovesStoredConversation() {
        FileBasedChatMemoryRepository repository = new FileBasedChatMemoryRepository(tempDir.toString());
        String conversationId = "session-to-delete";
        repository.saveAll(conversationId, List.of(new UserMessage("Test")));

        repository.deleteByConversationId(conversationId);

        assertTrue(repository.findByConversationId(conversationId).isEmpty());
        assertTrue(repository.findConversationIds().isEmpty());
    }
}
