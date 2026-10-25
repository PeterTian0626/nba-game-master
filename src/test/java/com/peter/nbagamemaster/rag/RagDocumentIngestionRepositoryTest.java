package com.peter.nbagamemaster.rag;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagDocumentIngestionRepositoryTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final RagDocumentIngestionRepository repository = new RagDocumentIngestionRepository(jdbcTemplate);

    @Test
    @SuppressWarnings("unchecked")
    void readsTheBackfilledRegistryBySourceKey() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("source_key")).thenReturn("documents/trade.md");
        when(resultSet.getString("content_hash")).thenReturn("hash");
        when(resultSet.getString("pipeline_version")).thenReturn("legacy-v1");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<RagDocumentIngestionRepository.IngestionRecord> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        });

        RagDocumentIngestionRepository.IngestionRecord record = repository.findAll().get("documents/trade.md");

        assertEquals("hash", record.contentHash());
        assertEquals("legacy-v1", record.pipelineVersion());
    }

    @Test
    void upsertsOnlyTheFiveColumnsInTheExistingSchema() {
        repository.recordSuccess("documents/trade.md", "hash", "token-v2", 3);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq("documents/trade.md"), eq("hash"), eq("token-v2"), eq(3));
        assertTrue(sql.getValue().contains("ON CONFLICT (source_key) DO UPDATE"));
        assertTrue(sql.getValue().contains("ingested_at = EXCLUDED.ingested_at"));
    }
}
