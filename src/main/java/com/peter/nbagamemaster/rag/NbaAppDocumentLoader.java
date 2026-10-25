package com.peter.nbagamemaster.rag;


import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Component
public class NbaAppDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    NbaAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    public List<Document> loadMarkdowns() {
        List<Document> documents = new ArrayList<>();
        for (MarkdownSource source : discoverMarkdowns()) {
            documents.addAll(loadMarkdown(source));
        }
        return documents;
    }

    public List<MarkdownSource> discoverMarkdowns() {
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:documents/*.md");
            Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
            List<MarkdownSource> sources = new ArrayList<>();
            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                byte[] content = resource.getContentAsByteArray();
                String contentHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
                sources.add(new MarkdownSource("documents/" + fileName, fileName, contentHash,
                        new ByteArrayResource(content)));
            }
            return sources;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load markdown files", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public List<Document> loadMarkdown(MarkdownSource source) {
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withIncludeCodeBlock(false)
                .withIncludeBlockquote(false)
                .withAdditionalMetadata("filename", source.filename())
                .withAdditionalMetadata("source_key", source.sourceKey())
                .build();
        return new MarkdownDocumentReader(source.resource(), config).get();
    }

    public record MarkdownSource(String sourceKey, String filename, String contentHash, Resource resource) {
    }
}
