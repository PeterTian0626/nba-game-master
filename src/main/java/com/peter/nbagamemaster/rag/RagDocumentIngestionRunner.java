package com.peter.nbagamemaster.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
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
    private final MyKeywordEnricher myKeywordEnricher;
    private final MyTokenTextSplitter tokenTextSplitter;
    private final ObjectProvider<PgVectorDocumentIngestion> pgVectorDocumentIngestion;

    public RagDocumentIngestionRunner(
            VectorStore vectorStore,
            NbaAppDocumentLoader nbaAppDocumentLoader,
            MyKeywordEnricher myKeywordEnricher,
            MyTokenTextSplitter tokenTextSplitter,
            ObjectProvider<PgVectorDocumentIngestion> pgVectorDocumentIngestion
    ) {
        this.vectorStore = vectorStore;
        this.nbaAppDocumentLoader = nbaAppDocumentLoader;
        this.myKeywordEnricher = myKeywordEnricher;
        this.tokenTextSplitter = tokenTextSplitter;
        this.pgVectorDocumentIngestion = pgVectorDocumentIngestion;
    }

    @Override
    public void run(ApplicationArguments args) {
        PgVectorDocumentIngestion persistentIngestion = pgVectorDocumentIngestion.getIfAvailable();
        if (persistentIngestion != null) {
            persistentIngestion.ingest(nbaAppDocumentLoader.discoverMarkdowns());
            return;
        }
        List<Document> documents = tokenTextSplitter.splitCustomized(nbaAppDocumentLoader.loadMarkdowns());
        if (documents.isEmpty()) {
            log.info("RAG ingestion skipped. No embeddable documents found.");
            return;
        }
        List<Document> enrichedDocuments = myKeywordEnricher.enrich(documents);
        vectorStore.add(enrichedDocuments);
        log.info("RAG ingestion completed. {} documents added.", enrichedDocuments.size());
    }
}
