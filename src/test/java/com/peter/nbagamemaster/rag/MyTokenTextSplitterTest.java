package com.peter.nbagamemaster.rag;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyTokenTextSplitterTest {

    private final MyTokenTextSplitter splitter = new MyTokenTextSplitter(40);
    private final Encoding encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    @Test
    void splitsLongTextIntoSmallerChunksAndPreservesSourceMetadata() {
        String sentence = "The Lakers need shooting, defense, and roster flexibility to evaluate a trade.";
        Document document = new Document((sentence + " ").repeat(150), Map.of("filename", "trade.md"));

        List<Document> chunks = splitter.splitCustomized(List.of(document));

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.size() > splitter.splitDocuments(List.of(document)).size());
        assertTrue(chunks.stream().allMatch(chunk -> encoding.countTokens(chunk.getText()) <= 200));
        assertTrue(chunks.stream().allMatch(chunk -> "trade.md".equals(chunk.getMetadata().get("filename"))));
        assertTrue(chunks.stream().allMatch(chunk -> document.getId().equals(chunk.getMetadata().get("parent_document_id"))));
        assertEquals(0, chunks.get(0).getMetadata().get("chunk_index"));
        assertTrue(chunks.stream().allMatch(chunk -> Integer.valueOf(chunks.size())
                .equals(chunk.getMetadata().get("total_chunks"))));
    }

    @Test
    void ignoresEmptyDocumentsAndFragmentsBelowTheConfiguredMinimum() {
        assertTrue(splitter.splitCustomized(List.of()).isEmpty());
        assertTrue(splitter.splitDocuments(List.of()).isEmpty());
        assertTrue(splitter.splitCustomized(List.of(new Document("   "), new Document("tiny"))).isEmpty());
    }

    @Test
    void adjacentChunksShareFortyTokensWithoutLosingSourceContent() {
        String text = IntStream.range(0, 600).mapToObj(index -> "player" + index).collect(Collectors.joining("|"));

        List<Document> chunks = splitter.splitCustomized(List.of(new Document(text)));

        assertCoverage(text, chunks, 40);
        int secondStart = text.indexOf(chunks.get(1).getText());
        assertEquals(40, encoding.countTokens(text.substring(secondStart, chunks.get(0).getText().length())));
    }

    @Test
    void overlapCanBeDisabled() {
        String text = IntStream.range(0, 600).mapToObj(index -> "player" + index).collect(Collectors.joining("|"));

        List<Document> chunks = new MyTokenTextSplitter(0).splitCustomized(List.of(new Document(text)));

        assertCoverage(text, chunks, 0);
        assertEquals(text, chunks.stream().map(Document::getText).collect(Collectors.joining()));
    }

    @Test
    void springUsesTheDefaultOverlapAndBindsAnExplicitOverride() {
        String text = IntStream.range(0, 600).mapToObj(index -> "player" + index).collect(Collectors.joining("|"));
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(MyTokenTextSplitter.class);

        contextRunner.run(context -> assertCoverage(text,
                context.getBean(MyTokenTextSplitter.class).splitCustomized(List.of(new Document(text))), 40));
        contextRunner.withPropertyValues("app.rag.text-splitter.chunk-overlap=0").run(context -> assertCoverage(text,
                context.getBean(MyTokenTextSplitter.class).splitCustomized(List.of(new Document(text))), 0));
    }

    @Test
    void preservesUnicodeAtChunkAndOverlapBoundaries() {
        String text = IntStream.range(0, 150)
                .mapToObj(index -> "第" + index + "名球员🏀分析交易价值🚀")
                .collect(Collectors.joining("|"));

        List<Document> chunks = splitter.splitCustomized(List.of(new Document(text)));

        assertCoverage(text, chunks, 40);
        assertTrue(chunks.stream().noneMatch(chunk -> chunk.getText().contains("\uFFFD")));
    }

    @Test
    void highOverlapStillAdvancesAndTerminates() {
        String text = IntStream.range(0, 90).mapToObj(index -> "player" + index).collect(Collectors.joining("|"));

        List<Document> chunks = new MyTokenTextSplitter(199).splitCustomized(List.of(new Document(text)));

        assertCoverage(text, chunks, 199);
    }

    @Test
    void overlapDoesNotCrossInputDocumentBoundaries() {
        Document first = new Document("Lakers shooting and defense analysis. ".repeat(60), Map.of("filename", "first.md"));
        Document second = new Document("Celtics roster and salary analysis. ".repeat(60), Map.of("filename", "second.md"));

        List<Document> chunks = splitter.splitCustomized(List.of(first, second));

        assertTrue(chunks.stream().anyMatch(chunk -> first.getId().equals(chunk.getMetadata().get("parent_document_id"))));
        assertTrue(chunks.stream().anyMatch(chunk -> second.getId().equals(chunk.getMetadata().get("parent_document_id"))));
        assertTrue(chunks.stream().filter(chunk -> "first.md".equals(chunk.getMetadata().get("filename")))
                .noneMatch(chunk -> chunk.getText().contains("Celtics")));
        assertTrue(chunks.stream().filter(chunk -> "second.md".equals(chunk.getMetadata().get("filename")))
                .noneMatch(chunk -> chunk.getText().contains("Lakers")));
    }

    @Test
    void smallDocumentsRemainSingleChunksAndDefaultSplittingIsUnchanged() {
        String text = "The Lakers need shooting and defense.\nSalary flexibility matters.";

        assertEquals(List.of(text), splitter.splitCustomized(List.of(new Document(text))).stream().map(Document::getText).toList());
        assertEquals(List.of(text), splitter.splitDocuments(List.of(new Document(text))).stream().map(Document::getText).toList());
    }

    @Test
    void splitsTheBundledCorpusWithinTheTokenBudget() {
        List<Document> documents = new NbaAppDocumentLoader(new PathMatchingResourcePatternResolver()).loadMarkdowns();

        List<Document> chunks = splitter.splitCustomized(documents);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> encoding.countTokens(chunk.getText()) <= 200));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getMetadata().containsKey("filename")));
        Map<String, String> originalTexts = documents.stream().collect(Collectors.toMap(Document::getId, Document::getText));
        assertTrue(chunks.stream().allMatch(chunk -> originalTexts.get(chunk.getMetadata().get("parent_document_id"))
                .contains(chunk.getText())));
    }

    @Test
    void rejectsInvalidOverlap() {
        assertThrows(IllegalArgumentException.class, () -> new MyTokenTextSplitter(-1));
        assertThrows(IllegalArgumentException.class, () -> new MyTokenTextSplitter(200));
    }

    @Test
    void failsInsteadOfEmittingAnOversizedRemainderWhenTheChunkLimitIsReached() {
        MyTokenTextSplitter highOverlapSplitter = new MyTokenTextSplitter(199);

        assertThrows(IllegalArgumentException.class, () -> highOverlapSplitter.splitCustomized(
                List.of(new Document("basketball ".repeat(5300)))));
    }

    private void assertCoverage(String text, List<Document> chunks, int maximumOverlap) {
        assertTrue(chunks.size() > 1);
        int previousStart = -1;
        int previousEnd = 0;
        for (Document chunk : chunks) {
            int start = text.indexOf(chunk.getText(), previousStart + 1);
            int end = start + chunk.getText().length();
            assertTrue(start >= 0 && start <= previousEnd, "Chunk must be part of the source without gaps");
            assertTrue(end > previousEnd, "Every chunk must include new source content");
            assertTrue(encoding.countTokens(chunk.getText()) <= 200);
            if (previousStart >= 0) {
                assertEquals(maximumOverlap > 0, start < previousEnd);
                assertTrue(encoding.countTokens(text.substring(start, previousEnd)) <= maximumOverlap);
            }
            previousStart = start;
            previousEnd = end;
        }
        assertEquals(text.length(), previousEnd);
    }
}
