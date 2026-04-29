package com.peter.nbagamemaster.demo.invoke;

import com.peter.nbagamemaster.NbaGameMasterApplication;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class SpringAiInvoke {

    public static void main(String[] args) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(NbaGameMasterApplication.class)
                .web(WebApplicationType.NONE)
                .run(args)) {
            ChatClient.Builder chatClientBuilder = context.getBean(ChatClient.Builder.class);
            String outputText = chatClientBuilder.build()
                    .prompt()
                    .user("Say wolf!")
                    .call()
                    .content();

            System.out.println(outputText);
        }
    }
}
