package com.peter.nbagamemaster.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NbaAppVectoreStoreConfig {
    @Resource
    private NbaAppDocumentLoader nbaAppDocumentLoader;

    @Bean
    @ConditionalOnProperty(name = "app.vector-store.type", havingValue = "simple", matchIfMissing = true)
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
        simpleVectorStore.add(nbaAppDocumentLoader.loadMarkdowns());
        return simpleVectorStore;
    }
}
