package com.peter.nbagamemaster.rag;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagDocumentIngestionRunnerTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final NbaAppDocumentLoader loader = mock(NbaAppDocumentLoader.class);
    private final MyKeywordEnricher enricher = mock(MyKeywordEnricher.class);
    private final MyTokenTextSplitter splitter = mock(MyTokenTextSplitter.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<PgVectorDocumentIngestion> persistentProvider = mock(ObjectProvider.class);
    private final RagDocumentIngestionRunner runner = new RagDocumentIngestionRunner(
            vectorStore, loader, enricher, splitter, persistentProvider);

    @Test
    void simpleStoreIsPopulatedOnlyByTheRunnerInSplitThenEnrichOrder() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        new NbaAppVectoreStoreConfig().vectorStore(embeddingModel);
        verifyNoInteractions(embeddingModel);
        List<Document> parsed = List.of(new Document("Parsed source"));
        List<Document> chunks = List.of(new Document("Split chunk"));
        List<Document> enriched = List.of(new Document("Enriched chunk"));
        when(loader.loadMarkdowns()).thenReturn(parsed);
        when(splitter.splitCustomized(parsed)).thenReturn(chunks);
        when(enricher.enrich(chunks)).thenReturn(enriched);

        runner.run(null);

        InOrder order = inOrder(loader, splitter, enricher, vectorStore);
        order.verify(loader).loadMarkdowns();
        order.verify(splitter).splitCustomized(parsed);
        order.verify(enricher).enrich(chunks);
        order.verify(vectorStore).add(enriched);
        order.verifyNoMoreInteractions();
    }

    @Test
    void delegatesPersistentIngestionWithoutAddingDocumentsTwice() {
        PgVectorDocumentIngestion persistentIngestion = mock(PgVectorDocumentIngestion.class);
        when(persistentProvider.getIfAvailable()).thenReturn(persistentIngestion);
        when(loader.discoverMarkdowns()).thenReturn(List.of());

        runner.run(null);

        verify(persistentIngestion).ingest(List.of());
        verifyNoInteractions(enricher, splitter, vectorStore);
    }

    @Test
    void skipsModelCallsForAnEmptySimpleCorpus() {
        when(loader.loadMarkdowns()).thenReturn(List.of());
        when(splitter.splitCustomized(List.of())).thenReturn(List.of());

        runner.run(null);

        verifyNoInteractions(enricher, vectorStore);
    }
}
