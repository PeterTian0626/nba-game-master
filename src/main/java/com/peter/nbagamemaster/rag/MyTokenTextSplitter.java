package com.peter.nbagamemaster.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MyTokenTextSplitter {

    private final TokenTextSplitter defaultSplitter = new TokenTextSplitter();
    private final TextSplitter customizedSplitter;

    public MyTokenTextSplitter(@Value("${app.rag.text-splitter.chunk-overlap:40}") int chunkOverlap) {
        this.customizedSplitter = new OverlappingTokenTextSplitter(chunkOverlap);
    }

    public List<Document> splitDocuments(List<Document> documents) {
        return defaultSplitter.apply(documents);
    }

    public List<Document> splitCustomized(List<Document> documents) {
        return customizedSplitter.apply(documents);
    }
}
