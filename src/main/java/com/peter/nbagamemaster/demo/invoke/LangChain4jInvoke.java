//package com.peter.nbagamemaster.demo.invoke;
//
//import dev.langchain4j.model.openai.OpenAiChatModel;
//
//public class LangChain4jInvoke {
//    public static void main(String[] args){
//        String apiKey = TestApiKey.requireApiKey();
//        OpenAiChatModel model = OpenAiChatModel.builder()
//                .apiKey(apiKey)
//                .modelName("gpt-4.1-nano-2025-04-14")
//                .build();
//        String answer = model.chat("Say wolf");
//        System.out.println(answer);
//    }
//}
