package com.peter.nbagamemaster.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(properties = {
        "app.vector-store.type=pgvector",
        "spring.datasource.url=jdbc:h2:mem:rag_registry;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RagDocumentIngestionRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RagDocumentIngestionRepositoryTest {

    private static final String SOURCE_KEY = "documents/trade.md";
    private static final String CONTENT_HASH = "a".repeat(64);

    @Autowired
    private RagDocumentIngestionRepository repository;

    @Autowired
    private RagDocumentIngestionJpaRepository jpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetDatabase() {
        jpaRepository.deleteAllInBatch();
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS rag_vector_transaction_test (content TEXT NOT NULL)");
        jdbcTemplate.update("DELETE FROM rag_vector_transaction_test");
    }

    @Test
    void readsExistingBackfilledRowsWithoutReingestion() {
        jdbcTemplate.update("""
                INSERT INTO public.rag_document_ingestion
                    (source_key, content_hash, pipeline_version, chunk_count, ingested_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, SOURCE_KEY, CONTENT_HASH, "legacy-v1", 3);

        RagDocumentIngestionRepository.IngestionRecord record = repository.findAll().get(SOURCE_KEY);

        assertEquals(CONTENT_HASH, record.contentHash());
        assertEquals("legacy-v1", record.pipelineVersion());
    }

    @Test
    void insertsAndUpdatesOneRegistrationPerSource() {
        repository.recordSuccess(SOURCE_KEY, CONTENT_HASH, "token-v2", 3);
        Instant firstIngestion = jpaRepository.findById(SOURCE_KEY).orElseThrow().getIngestedAt();

        repository.recordSuccess(SOURCE_KEY, "b".repeat(64), "token-v3", 5);

        RagDocumentIngestionEntity entity = jpaRepository.findById(SOURCE_KEY).orElseThrow();
        assertEquals(1, jpaRepository.count());
        assertEquals("b".repeat(64), entity.getContentHash());
        assertEquals("token-v3", entity.getPipelineVersion());
        assertEquals(5, entity.getChunkCount());
        assertFalse(entity.getIngestedAt().isBefore(firstIngestion));
    }

    @Test
    void commitsJpaRegistryAndJdbcWritesTogether() {
        assertInstanceOf(JpaTransactionManager.class, transactionManager);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO rag_vector_transaction_test (content) VALUES (?)", "new vector");
            repository.recordSuccess(SOURCE_KEY, CONTENT_HASH, "token-v3", 1);
        });

        assertEquals(1, jpaRepository.count());
        assertEquals("new vector", jdbcTemplate.queryForObject(
                "SELECT content FROM rag_vector_transaction_test", String.class));
    }

    @Test
    void rollsBackBothJpaAndJdbcInsertsOnFailure() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    jdbcTemplate.update("INSERT INTO rag_vector_transaction_test (content) VALUES (?)", "new vector");
                    repository.recordSuccess(SOURCE_KEY, CONTENT_HASH, "token-v3", 1);
                    throw new IllegalStateException("Ingestion failed");
                }));

        assertEquals(0, jpaRepository.count());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM rag_vector_transaction_test", Integer.class));
    }

    @Test
    void preservesThePreviousRegistrationAndJdbcContentWhenReplacementFails() {
        repository.recordSuccess(SOURCE_KEY, CONTENT_HASH, "token-v2", 3);
        jdbcTemplate.update("INSERT INTO rag_vector_transaction_test (content) VALUES (?)", "old vector");

        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    jdbcTemplate.update("DELETE FROM rag_vector_transaction_test");
                    jdbcTemplate.update("INSERT INTO rag_vector_transaction_test (content) VALUES (?)", "new vector");
                    repository.recordSuccess(SOURCE_KEY, "b".repeat(64), "token-v3", 1);
                    throw new IllegalStateException("Replacement failed");
                }));

        RagDocumentIngestionEntity entity = jpaRepository.findById(SOURCE_KEY).orElseThrow();
        assertEquals(CONTENT_HASH, entity.getContentHash());
        assertEquals("token-v2", entity.getPipelineVersion());
        assertEquals(3, entity.getChunkCount());
        assertEquals("old vector", jdbcTemplate.queryForObject(
                "SELECT content FROM rag_vector_transaction_test", String.class));
    }
}
