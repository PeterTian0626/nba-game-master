package com.peter.nbagamemaster.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(name = "app.vector-store.type", havingValue = "pgvector")
public class RagDocumentIngestionRepository {

    private final RagDocumentIngestionJpaRepository jpaRepository;

    public RagDocumentIngestionRepository(RagDocumentIngestionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    public Map<String, IngestionRecord> findAll() {
        return jpaRepository.findAll().stream()
                .map(entity -> new IngestionRecord(entity.getSourceKey(), entity.getContentHash(), entity.getPipelineVersion()))
                .collect(Collectors.toMap(IngestionRecord::sourceKey, Function.identity()));
    }

    public void recordSuccess(String sourceKey, String contentHash, String pipelineVersion, int chunkCount) {
        jpaRepository.saveAndFlush(new RagDocumentIngestionEntity(
                sourceKey, contentHash, pipelineVersion, chunkCount, Instant.now()));
    }

    public record IngestionRecord(String sourceKey, String contentHash, String pipelineVersion) {
    }
}
