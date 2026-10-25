package com.peter.nbagamemaster.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(name = "app.vector-store.type", havingValue = "pgvector")
public class RagDocumentIngestionRepository {

    private final JdbcTemplate jdbcTemplate;

    public RagDocumentIngestionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, IngestionRecord> findAll() {
        return jdbcTemplate.query("""
                SELECT source_key, content_hash, pipeline_version
                FROM public.rag_document_ingestion
                """, (resultSet, rowNumber) -> new IngestionRecord(
                resultSet.getString("source_key"),
                resultSet.getString("content_hash"),
                resultSet.getString("pipeline_version")
        )).stream().collect(Collectors.toMap(IngestionRecord::sourceKey, Function.identity()));
    }

    public void recordSuccess(String sourceKey, String contentHash, String pipelineVersion, int chunkCount) {
        jdbcTemplate.update("""
                INSERT INTO public.rag_document_ingestion
                    (source_key, content_hash, pipeline_version, chunk_count, ingested_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (source_key) DO UPDATE SET
                    content_hash = EXCLUDED.content_hash,
                    pipeline_version = EXCLUDED.pipeline_version,
                    chunk_count = EXCLUDED.chunk_count,
                    ingested_at = EXCLUDED.ingested_at
                """, sourceKey, contentHash, pipelineVersion, chunkCount);
    }

    public record IngestionRecord(String sourceKey, String contentHash, String pipelineVersion) {
    }
}
