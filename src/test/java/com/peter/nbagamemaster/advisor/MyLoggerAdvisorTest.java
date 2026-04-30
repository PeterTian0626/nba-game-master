package com.peter.nbagamemaster.advisor;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;


import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyLoggerAdvisorTest {

    private TestAppender appender;
    private Logger logger;

    @BeforeEach
    void setup() {
        logger = (Logger) LoggerFactory.getLogger(MyLoggerAdvisor.class);
        appender = new TestAppender();
        appender.setName("TEST");
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void teardown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void adviseCall_logsRequestAndResponse() {
        MyLoggerAdvisor advisor = new MyLoggerAdvisor();

        // prepare a request with a user message
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt("Hello AI, how are you?"))
                .build();

        // mock chain and response; create a separate ChatResponse mock and attach it to ChatClientResponse
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientResponse response = mock(ChatClientResponse.class);
        org.springframework.ai.chat.model.ChatResponse chatResp = mock(org.springframework.ai.chat.model.ChatResponse.class, org.mockito.Answers.RETURNS_DEEP_STUBS);

        // stub the nested result/output/text on the ChatResponse mock, then return that from response.chatResponse()
        when(chatResp.getResult().getOutput().getText()).thenReturn("I am fine");
        doReturn(chatResp).when(response).chatResponse();
        doReturn(response).when(chain).nextCall(request);

        // call
        ChatClientResponse returned = advisor.adviseCall(request, chain);

        // verify returned
        assertThat(returned).isSameAs(response);

        // capture logs
        List<String> messages = appender.getMessages();
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("AI Request: Hello AI, how are you?"));
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("AI Response: I am fine"));
    }

    @Test
    void adviseStream_logsResponseForStreamedResponses() {
        MyLoggerAdvisor advisor = new MyLoggerAdvisor();

        // Instead of invoking adviseStream (which uses internal aggregation logic), invoke the
        // private before(...) and observeAfter(...) methods via reflection to assert logging.
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt("Stream me"))
                .build();

        try {
            var before = MyLoggerAdvisor.class.getDeclaredMethod("before", ChatClientRequest.class);
            before.setAccessible(true);
            before.invoke(advisor, request);

            // prepare a ChatClientResponse with a real ChatResponse mock to pass to observeAfter
            ChatClientResponse response = mock(ChatClientResponse.class);
            org.springframework.ai.chat.model.ChatResponse chatResp = mock(org.springframework.ai.chat.model.ChatResponse.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
            when(chatResp.getResult().getOutput().getText()).thenReturn("streamed text");
            doReturn(chatResp).when(response).chatResponse();

            var observeAfter = MyLoggerAdvisor.class.getDeclaredMethod("observeAfter", ChatClientResponse.class);
            observeAfter.setAccessible(true);
            observeAfter.invoke(advisor, response);

        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        List<String> messages = appender.getMessages();
        // request should be logged
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("AI Request: Stream me"));
        // streamed response should be logged
        assertThat(messages).anySatisfy(m -> assertThat(m).contains("AI Response: streamed text"));
    }

    // small logback appender to collect formatted log messages for assertions
    static class TestAppender extends AppenderBase<ILoggingEvent> {
        private final List<ILoggingEvent> events = new ArrayList<>();

        @Override
        protected void append(ILoggingEvent e) {
            events.add(e);
        }

        List<String> getMessages() {
            List<String> msgs = new ArrayList<>();
            for (ILoggingEvent e : events) {
                msgs.add(e.getFormattedMessage());
            }
            return msgs;
        }
    }
}

