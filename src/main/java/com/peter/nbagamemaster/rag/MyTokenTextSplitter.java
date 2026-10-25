package com.peter.nbagamemaster.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MyTokenTextSplitter {

    private final TokenTextSplitter defaultSplitter = new TokenTextSplitter();
    private final TokenTextSplitter customizedSplitter = TokenTextSplitter.builder()
            .withChunkSize(200)
            .withMinChunkSizeChars(100)
            .withMinChunkLengthToEmbed(10)
            .withMaxNumChunks(5000)
            .withKeepSeparator(true)
            .build();

    public List<Document> splitDocuments(List<Document> documents) {
        return defaultSplitter.apply(documents);
    }

    public List<Document> splitCustomized(List<Document> documents) {
        return customizedSplitter.apply(documents);
    }
}
