package com.peter.nbagamemaster.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@ConditionalOnProperty(name = "app.rag.ingestion.enabled", havingValue = "true")
public class RagDocumentIngestionRunner implements ApplicationRunner {

    private final VectorStore vectorStore;
    private final NbaAppDocumentLoader nbaAppDocumentLoader;

    public RagDocumentIngestionRunner(
            VectorStore vectorStore,
            NbaAppDocumentLoader nbaAppDocumentLoader
    ) {
        this.vectorStore = vectorStore;
        this.nbaAppDocumentLoader = nbaAppDocumentLoader;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Document> documents = nbaAppDocumentLoader.loadMarkdowns();
        vectorStore.add(documents);
        log.info("RAG ingestion completed. {} documents added.", documents.size());
    }
}
