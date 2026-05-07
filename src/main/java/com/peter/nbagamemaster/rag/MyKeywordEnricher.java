package com.peter.nbagamemaster.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Custom keyword enricher for document metadata using ChatModel for keyword extraction.
 */
@Component
public class MyKeywordEnricher {

    @Resource
    private ChatModel chatModel;

    public List<Document> enrich(List<Document> documents) {
        KeywordMetadataEnricher enricher = KeywordMetadataEnricher.builder(chatModel)
                .keywordCount(5)
                .build();

        return enricher.apply(documents);
    }
}
