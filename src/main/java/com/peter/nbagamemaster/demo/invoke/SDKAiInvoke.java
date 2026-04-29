package com.peter.nbagamemaster.demo.invoke;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;

public class SDKAiInvoke {
    public static void main(String[] args) {
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(TestApiKey.requireApiKey())
                .build();

        ResponseCreateParams params = ResponseCreateParams.builder()
                .input("Say this is a test")
                .model("gpt-4.1-nano-2025-04-14")
                .build();

        Response response = client.responses().create(params);
        String outputText = response.output().stream()
                .filter(ResponseOutputItem::isMessage)
                .map(ResponseOutputItem::asMessage)
                .flatMap(message -> message.content().stream())
                .filter(ResponseOutputMessage.Content::isOutputText)
                .map(ResponseOutputMessage.Content::asOutputText)
                .map(ResponseOutputText::text)
                .findFirst()
                .orElse("");

        System.out.println(outputText);
    }
}
