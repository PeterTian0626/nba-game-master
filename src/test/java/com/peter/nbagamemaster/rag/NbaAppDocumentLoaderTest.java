package com.peter.nbagamemaster.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NbaAppDocumentLoaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversBundledSourcesWithStableKeysAndHashes() {
        NbaAppDocumentLoader loader = new NbaAppDocumentLoader(new PathMatchingResourcePatternResolver());

        List<NbaAppDocumentLoader.MarkdownSource> sources = loader.discoverMarkdowns();

        assertEquals(5, sources.size());
        assertTrue(sources.stream().allMatch(source -> source.contentHash().matches("[0-9a-f]{64}")));
        assertTrue(sources.stream().allMatch(source -> source.sourceKey().equals("documents/" + source.filename())));
        assertEquals(sources.stream().map(NbaAppDocumentLoader.MarkdownSource::contentHash).toList(),
                loader.discoverMarkdowns().stream().map(NbaAppDocumentLoader.MarkdownSource::contentHash).toList());
    }

    @Test
    void hashesExactSourceBytesAndParsesTheSameSnapshot() throws IOException {
        Path sourcePath = temporaryDirectory.resolve("example.md");
        Files.writeString(sourcePath, "abc");
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources("classpath:documents/*.md"))
                .thenReturn(new Resource[]{new FileSystemResource(sourcePath)});
        NbaAppDocumentLoader loader = new NbaAppDocumentLoader(resolver);

        NbaAppDocumentLoader.MarkdownSource source = loader.discoverMarkdowns().get(0);
        Files.writeString(sourcePath, "changed content");

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", source.contentHash());
        assertEquals("abc", loader.loadMarkdown(source).get(0).getText().strip());
        assertFalse(source.contentHash().equals(loader.discoverMarkdowns().get(0).contentHash()));
    }

    @Test
    void preservesSourceMetadataAndSeparatesCodeAndBlockquotes() {
        NbaAppDocumentLoader loader = new NbaAppDocumentLoader(new PathMatchingResourcePatternResolver());
        String markdown = """
                # Lakers

                Trade analysis text.

                ```java
                excludedCode();
                ```

                > excludedQuote
                """;
        NbaAppDocumentLoader.MarkdownSource source = new NbaAppDocumentLoader.MarkdownSource(
                "documents/example.md", "example.md", "hash",
                new ByteArrayResource(markdown.getBytes(StandardCharsets.UTF_8)));

        List<Document> documents = loader.loadMarkdown(source);

        assertFalse(documents.isEmpty());
        assertTrue(documents.stream().allMatch(document -> "example.md".equals(document.getMetadata().get("filename"))));
        assertTrue(documents.stream().allMatch(document -> source.sourceKey().equals(document.getMetadata().get("source_key"))));
        String content = documents.stream().map(Document::getText).reduce("", String::concat);
        assertTrue(content.contains("Trade analysis text."));
        assertTrue(documents.stream().anyMatch(document -> document.getText().contains("excludedCode")
                && "code_block".equals(document.getMetadata().get("category"))));
        assertTrue(documents.stream().anyMatch(document -> document.getText().contains("excludedQuote")
                && "blockquote".equals(document.getMetadata().get("category"))));
    }

    @Test
    void failsInsteadOfReturningAPartialCorpusWhenAResourceCannotBeRead() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        Resource brokenResource = mock(Resource.class);
        when(brokenResource.getFilename()).thenReturn("broken.md");
        when(brokenResource.getContentAsByteArray()).thenThrow(new IOException("Cannot read file"));
        when(resolver.getResources("classpath:documents/*.md")).thenReturn(new Resource[]{brokenResource});

        assertThrows(UncheckedIOException.class, () -> new NbaAppDocumentLoader(resolver).discoverMarkdowns());
    }

    @Test
    void handlesNoMatchingResources() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources("classpath:documents/*.md")).thenReturn(new Resource[0]);

        assertTrue(new NbaAppDocumentLoader(resolver).loadMarkdowns().isEmpty());
    }
}
