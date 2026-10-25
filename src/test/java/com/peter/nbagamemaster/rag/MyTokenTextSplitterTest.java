package com.peter.nbagamemaster.rag;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyTokenTextSplitterTest {

    private final MyTokenTextSplitter splitter = new MyTokenTextSplitter();

    @Test
    void splitsLongTextIntoSmallerChunksAndPreservesSourceMetadata() {
        String sentence = "The Lakers need shooting, defense, and roster flexibility to evaluate a trade.";
        Document document = new Document((sentence + " ").repeat(150), Map.of("filename", "trade.md"));
        Encoding encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

        List<Document> chunks = splitter.splitCustomized(List.of(document));

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.size() > splitter.splitDocuments(List.of(document)).size());
        assertTrue(chunks.stream().allMatch(chunk -> encoding.countTokens(chunk.getText()) <= 200));
        assertTrue(chunks.stream().allMatch(chunk -> "trade.md".equals(chunk.getMetadata().get("filename"))));
        assertTrue(chunks.stream().allMatch(chunk -> document.getId().equals(chunk.getMetadata().get("parent_document_id"))));
        assertEquals(document.getText().strip(), String.join(" ", chunks.stream().map(Document::getText).toList()));
    }

    @Test
    void ignoresEmptyDocumentsAndFragmentsBelowTheConfiguredMinimum() {
        assertTrue(splitter.splitCustomized(List.of()).isEmpty());
        assertTrue(splitter.splitDocuments(List.of()).isEmpty());
        assertTrue(splitter.splitCustomized(List.of(new Document("   "), new Document("tiny"))).isEmpty());
    }
}
