package com.peter.nbagamemaster.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NbaAppVectoreStoreConfig {
    @Resource
    private NbaAppDocumentLoader nbaAppDocumentLoader;

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
        simpleVectorStore.add(nbaAppDocumentLoader.loadMarkdowns());
        return simpleVectorStore;
    }
}
