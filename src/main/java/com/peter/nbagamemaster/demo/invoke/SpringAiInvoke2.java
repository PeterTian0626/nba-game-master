package com.peter.nbagamemaster.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;



//@Component
//public class SpringAiInvoke2 implements CommandLineRunner{
//    @Resource
//    private ChatModel openAIChatModel;
//
//    @Override
//    public void run(String... args) throws Exception{
//        AssistantMessage assistantMessage = openAIChatModel.call(new Prompt("say wolf"))
//                .getResult()
//                .getOutput();
//
//        System.out.println(assistantMessage.getText());
//    }
//}
