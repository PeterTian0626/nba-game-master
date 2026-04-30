package com.peter.nbagamemaster.chatMemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

public class FileBasedChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(FileBasedChatMemoryRepository.class);
    private static final String FILE_EXTENSION = ".kryo";
    private static final Base64.Encoder FILE_NAME_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder FILE_NAME_DECODER = Base64.getUrlDecoder();
    private static final ThreadLocal<Kryo> KRYO = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
        return kryo;
    });

    private final Path baseDir;

    public FileBasedChatMemoryRepository(String dir) {
        Assert.hasText(dir, "dir cannot be null or empty");
        this.baseDir = Path.of(dir);
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create chat memory directory: " + this.baseDir, e);
        }
    }

    @Override
    public List<String> findConversationIds() {
        try (Stream<Path> files = Files.list(this.baseDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(FILE_EXTENSION))
                    .map(name -> name.substring(0, name.length() - FILE_EXTENSION.length()))
                    .map(this::decodeConversationId)
                    .filter(conversationId -> !conversationId.isBlank())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list stored conversations", e);
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Path conversationFile = resolveConversationFile(conversationId);
        if (Files.notExists(conversationFile)) {
            return List.of();
        }

        try (Input input = new Input(Files.newInputStream(conversationFile, StandardOpenOption.READ))) {
            List<Message> messages = KRYO.get().readObject(input, ArrayList.class);
            return List.copyOf(messages);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read chat memory for conversation: " + conversationId, e);
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");

        Path conversationFile = resolveConversationFile(conversationId);
        List<Message> messagesToStore = new ArrayList<>(messages);
        try (Output output = new Output(Files.newOutputStream(
                conversationFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE))) {
            KRYO.get().writeObject(output, messagesToStore);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save chat memory for conversation: " + conversationId, e);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Path conversationFile = resolveConversationFile(conversationId);
        try {
            Files.deleteIfExists(conversationFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete chat memory for conversation: " + conversationId, e);
        }
    }

    private Path resolveConversationFile(String conversationId) {
        String encodedConversationId = FILE_NAME_ENCODER.encodeToString(conversationId.getBytes(StandardCharsets.UTF_8));
        return this.baseDir.resolve(encodedConversationId + FILE_EXTENSION);
    }

    private String decodeConversationId(String encodedConversationId) {
        try {
            byte[] decoded = FILE_NAME_DECODER.decode(encodedConversationId);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.warn("Skipping chat memory file with invalid conversation id encoding: {}", encodedConversationId);
            return "";
        }
    }
}
