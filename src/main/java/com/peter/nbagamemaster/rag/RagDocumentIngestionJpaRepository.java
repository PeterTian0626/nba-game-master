package com.peter.nbagamemaster.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;

@ConditionalOnProperty(name = "app.vector-store.type", havingValue = "pgvector")
public interface RagDocumentIngestionJpaRepository extends JpaRepository<RagDocumentIngestionEntity, String> {
}
