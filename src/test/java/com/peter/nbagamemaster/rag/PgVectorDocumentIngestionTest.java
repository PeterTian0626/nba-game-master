package com.peter.nbagamemaster.rag;

import com.peter.nbagamemaster.rag.NbaAppDocumentLoader.MarkdownSource;
import com.peter.nbagamemaster.rag.RagDocumentIngestionRepository.IngestionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PgVectorDocumentIngestionTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final NbaAppDocumentLoader loader = mock(NbaAppDocumentLoader.class);
    private final MyTokenTextSplitter splitter = mock(MyTokenTextSplitter.class);
    private final MyKeywordEnricher enricher = mock(MyKeywordEnricher.class);
    private final RagDocumentIngestionRepository repository = mock(RagDocumentIngestionRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private final MarkdownSource source = new MarkdownSource(
            "documents/trade.md", "trade.md", "current-hash", new ByteArrayResource(new byte[0]));
    private final List<Document> parsed = List.of(new Document("Original trade analysis"));
    private final List<Document> chunks = List.of(new Document("A trade analysis chunk"));
    private final List<Document> enriched = List.of(new Document("A trade analysis chunk", Map.of("keywords", "Lakers")));
    private PgVectorDocumentIngestion ingestion;

    @BeforeEach
    void setUp() {
        when(repository.findAll()).thenReturn(Map.of());
        when(loader.loadMarkdown(source)).thenReturn(parsed);
        when(splitter.splitCustomized(parsed)).thenReturn(chunks);
        when(enricher.enrich(chunks)).thenReturn(enriched);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        ingestion = new PgVectorDocumentIngestion(
                vectorStore, loader, splitter, enricher, repository, transactionManager, "token-v2");
    }

    @Test
    void skipsMatchingSourcesBeforeParsingOrModelCalls() {
        when(repository.findAll()).thenReturn(Map.of(source.sourceKey(),
                new IngestionRecord(source.sourceKey(), source.contentHash(), "token-v2")));

        ingestion.ingest(List.of(source));

        verifyNoInteractions(loader, splitter, enricher, vectorStore, transactionManager);
        verify(repository, never()).recordSuccess(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void processesNewSourcesInOrderAndCommitsVectorsWithTheRegistryRecord() {
        ingestion.ingest(List.of(source));

        InOrder order = inOrder(repository, loader, splitter, enricher, vectorStore, transactionManager);
        order.verify(repository).findAll();
        order.verify(loader).loadMarkdown(source);
        order.verify(splitter).splitCustomized(parsed);
        order.verify(enricher).enrich(chunks);
        order.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
        order.verify(vectorStore).delete(any(Filter.Expression.class));
        order.verify(vectorStore).add(any());
        order.verify(repository).recordSuccess(source.sourceKey(), source.contentHash(), "token-v2", 1);
        order.verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void replacesLegacySourcesBySourceKeyOrFilename() {
        when(repository.findAll()).thenReturn(Map.of(source.sourceKey(),
                new IngestionRecord(source.sourceKey(), source.contentHash(), "legacy-v1")));

        ingestion.ingest(List.of(source));

        FilterExpressionBuilder filter = new FilterExpressionBuilder();
        verify(vectorStore).delete(filter.or(filter.eq("source_key", source.sourceKey()),
                filter.eq("filename", source.filename())).build());
        verify(repository).recordSuccess(source.sourceKey(), source.contentHash(), "token-v2", 1);
    }

    @Test
    void reprocessesChangedContent() {
        when(repository.findAll()).thenReturn(Map.of(source.sourceKey(),
                new IngestionRecord(source.sourceKey(), "previous-hash", "token-v2")));

        ingestion.ingest(List.of(source));

        verify(enricher).enrich(chunks);
        verify(repository).recordSuccess(source.sourceKey(), source.contentHash(), "token-v2", 1);
    }

    @Test
    void reprocessesThePreviousPipelineWhenOverlapIsIntroduced() {
        when(repository.findAll()).thenReturn(Map.of(source.sourceKey(),
                new IngestionRecord(source.sourceKey(), source.contentHash(), "token-v2")));
        PgVectorDocumentIngestion overlappingIngestion = new PgVectorDocumentIngestion(
                vectorStore, loader, splitter, enricher, repository, transactionManager, "token-v3");

        overlappingIngestion.ingest(List.of(source));

        verify(enricher).enrich(chunks);
        verify(repository).recordSuccess(source.sourceKey(), source.contentHash(), "token-v3", 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void assignsStableChunkIdsAndRetainsKeywordsAndSourceMetadata() {
        List<Document> multipleChunks = List.of(enriched.get(0), enriched.get(0));
        when(splitter.splitCustomized(parsed)).thenReturn(multipleChunks);
        when(enricher.enrich(multipleChunks)).thenReturn(multipleChunks);

        ingestion.ingest(List.of(source));
        ingestion.ingest(List.of(source));

        ArgumentCaptor<List<Document>> captured = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).add(captured.capture());
        List<Document> first = captured.getAllValues().get(0);
        List<Document> second = captured.getAllValues().get(1);
        assertEquals(first.stream().map(Document::getId).toList(), second.stream().map(Document::getId).toList());
        assertNotEquals(first.get(0).getId(), first.get(1).getId());
        assertEquals(first.get(0).getId(), UUID.fromString(first.get(0).getId()).toString());
        assertEquals("Lakers", first.get(0).getMetadata().get("keywords"));
        assertEquals(source.sourceKey(), first.get(0).getMetadata().get("source_key"));
        assertEquals(source.contentHash(), first.get(0).getMetadata().get("content_hash"));
        assertEquals("token-v2", first.get(0).getMetadata().get("pipeline_version"));
        assertEquals(1, first.get(1).getMetadata().get("source_chunk_index"));
    }

    @Test
    void rollsBackVectorFailuresAndRetriesTheUnregisteredSourceNextTime() {
        doThrow(new IllegalStateException("Embedding or database failure")).doNothing().when(vectorStore).add(any());

        assertThrows(IllegalStateException.class, () -> ingestion.ingest(List.of(source)));

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
        verify(repository, never()).recordSuccess(anyString(), anyString(), anyString(), anyInt());

        ingestion.ingest(List.of(source));

        verify(enricher, times(2)).enrich(chunks);
        verify(repository).recordSuccess(source.sourceKey(), source.contentHash(), "token-v2", 1);
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void rollsBackWhenTheRegistryWriteFails() {
        doThrow(new IllegalStateException("Registry unavailable")).when(repository)
                .recordSuccess(anyString(), anyString(), anyString(), anyInt());

        assertThrows(IllegalStateException.class, () -> ingestion.ingest(List.of(source)));

        verify(vectorStore).add(any());
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void preservesExistingVectorsWhenEnrichmentFails() {
        when(enricher.enrich(chunks)).thenThrow(new IllegalStateException("Model unavailable"));

        assertThrows(IllegalStateException.class, () -> ingestion.ingest(List.of(source)));

        verifyNoInteractions(vectorStore, transactionManager);
        verify(repository, never()).recordSuccess(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void rejectsEmptyChunksWithoutDeletingExistingVectors() {
        when(splitter.splitCustomized(parsed)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> ingestion.ingest(List.of(source)));

        verifyNoInteractions(enricher, vectorStore, transactionManager);
    }

    @Test
    void skipsCompletedFilesAndProcessesOnlyNewFilesInAMixedCorpus() {
        MarkdownSource unchanged = new MarkdownSource("documents/old.md", "old.md", "old-hash", source.resource());
        when(repository.findAll()).thenReturn(Map.of(unchanged.sourceKey(),
                new IngestionRecord(unchanged.sourceKey(), unchanged.contentHash(), "token-v2")));

        ingestion.ingest(List.of(unchanged, source));

        verify(loader, never()).loadMarkdown(unchanged);
        verify(loader).loadMarkdown(source);
        verify(repository).recordSuccess(source.sourceKey(), source.contentHash(), "token-v2", 1);
        verify(transactionManager).commit(transactionStatus);
    }
}
