package com.peter.nbagamemaster.rag;

import com.peter.nbagamemaster.rag.NbaAppDocumentLoader.MarkdownSource;
import com.peter.nbagamemaster.rag.RagDocumentIngestionRepository.IngestionRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@ConditionalOnProperty(name = "app.vector-store.type", havingValue = "pgvector")
public class PgVectorDocumentIngestion {

    private final VectorStore vectorStore;
    private final NbaAppDocumentLoader documentLoader;
    private final MyTokenTextSplitter tokenTextSplitter;
    private final MyKeywordEnricher keywordEnricher;
    private final RagDocumentIngestionRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final String pipelineVersion;

    public PgVectorDocumentIngestion(
            VectorStore vectorStore,
            NbaAppDocumentLoader documentLoader,
            MyTokenTextSplitter tokenTextSplitter,
            MyKeywordEnricher keywordEnricher,
            RagDocumentIngestionRepository repository,
            PlatformTransactionManager transactionManager,
            @Value("${app.rag.ingestion.pipeline-version:token-v3}") String pipelineVersion
    ) {
        Assert.hasText(pipelineVersion, "pipelineVersion cannot be blank");
        this.vectorStore = vectorStore;
        this.documentLoader = documentLoader;
        this.tokenTextSplitter = tokenTextSplitter;
        this.keywordEnricher = keywordEnricher;
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.pipelineVersion = pipelineVersion;
    }

    public void ingest(List<MarkdownSource> sources) {
        Map<String, IngestionRecord> records = repository.findAll();
        int ingested = 0;
        for (MarkdownSource source : sources) {
            IngestionRecord record = records.get(source.sourceKey());
            if (record != null && record.contentHash().equals(source.contentHash())
                    && record.pipelineVersion().equals(pipelineVersion)) {
                log.info("Skipping unchanged RAG source {}", source.sourceKey());
                continue;
            }

            List<Document> chunks = tokenTextSplitter.splitCustomized(documentLoader.loadMarkdown(source));
            Assert.notEmpty(chunks, "No embeddable chunks for " + source.sourceKey());
            List<Document> enrichedDocuments = keywordEnricher.enrich(chunks);
            Assert.isTrue(enrichedDocuments.size() == chunks.size(), "Enrichment must preserve all chunks");
            List<Document> documents = identifyChunks(source, enrichedDocuments);

            transactionTemplate.executeWithoutResult(status -> {
                FilterExpressionBuilder filter = new FilterExpressionBuilder();
                vectorStore.delete(filter.or(
                        filter.eq("source_key", source.sourceKey()),
                        filter.eq("filename", source.filename())
                ).build());
                vectorStore.add(documents);
                repository.recordSuccess(source.sourceKey(), source.contentHash(), pipelineVersion, documents.size());
            });
            ingested++;
            log.info("RAG source {} ingested. {} chunks stored.", source.sourceKey(), documents.size());
        }
        log.info("RAG ingestion completed. {} sources ingested, {} unchanged sources skipped.",
                ingested, sources.size() - ingested);
    }

    private List<Document> identifyChunks(MarkdownSource source, List<Document> chunks) {
        List<Document> documents = new ArrayList<>();
        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
            Document chunk = chunks.get(chunkIndex);
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("source_key", source.sourceKey());
            metadata.put("filename", source.filename());
            metadata.put("content_hash", source.contentHash());
            metadata.put("pipeline_version", pipelineVersion);
            metadata.put("source_chunk_index", chunkIndex);
            String identity = source.sourceKey() + "\n" + source.contentHash() + "\n" + pipelineVersion + "\n" + chunkIndex;
            String chunkId = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
            Document document = Document.builder().id(chunkId).text(chunk.getText()).metadata(metadata).build();
            document.setContentFormatter(chunk.getContentFormatter());
            documents.add(document);
        }
        return documents;
    }
}
