package com.peter.nbagamemaster.rag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "rag_document_ingestion", schema = "public")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RagDocumentIngestionEntity {

    @Id
    @Column(name = "source_key", nullable = false, columnDefinition = "text")
    private String sourceKey;

    @Column(name = "content_hash", nullable = false, columnDefinition = "text")
    private String contentHash;

    @Column(name = "pipeline_version", nullable = false, columnDefinition = "text")
    private String pipelineVersion;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    public RagDocumentIngestionEntity(String sourceKey, String contentHash, String pipelineVersion,
                                      int chunkCount, Instant ingestedAt) {
        this.sourceKey = sourceKey;
        this.contentHash = contentHash;
        this.pipelineVersion = pipelineVersion;
        this.chunkCount = chunkCount;
        this.ingestedAt = ingestedAt;
    }
}
