package com.peter.nbagamemaster.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;


import java.util.List;
import java.util.Map;

@Slf4j
@SpringBootTest
class PgVectorVectorStoreConfigTest {

    @Resource
    private VectorStore pgVectorVectorStore;

    @Test
    void pgVectorVectorStore() {

//        List<Document> documents = List.of(
//                new Document("Luka Doncic is one of the best players in the NBA", Map.of("meta1", "meta1")),
//                new Document("Stephen Curry is the best shooter in the NBA", Map.of("meta1", "meta1")),
//                new Document("The Boston Celtics are one of the champion contenders today", Map.of("meta2", "meta2")));
//        pgVectorVectorStore.add(documents);

        List<Document> results = this.pgVectorVectorStore.similaritySearch(SearchRequest.builder().query("Which player is good this year in NBA").topK(2).build());
        log.info("Search results: {}", results);
    }
}