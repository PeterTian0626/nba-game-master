package com.peter.nbagamemaster.rag;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class OverlappingTokenTextSplitter extends TextSplitter {

    private static final int CHUNK_SIZE = 200;
    private static final int MIN_CHUNK_SIZE_CHARS = 100;
    private static final int MIN_CHUNK_LENGTH_TO_EMBED = 10;
    private static final int MAX_NUM_CHUNKS = 5000;
    private static final List<Character> PUNCTUATION_MARKS = List.of('.', '?', '!', '\n');

    private final Encoding encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    private final int chunkOverlap;

    OverlappingTokenTextSplitter(int chunkOverlap) {
        Assert.isTrue(chunkOverlap >= 0 && chunkOverlap < CHUNK_SIZE,
                "chunkOverlap must be between 0 and 199 tokens");
        this.chunkOverlap = chunkOverlap;
    }

    @Override
    protected List<String> splitText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        IntArrayList tokens = encoding.encode(text);
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        int[] offsets = tokenByteOffsets(tokens);
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int previousEnd = 0;
        int iterations = 0;

        while (start < tokens.size()) {
            int end = Math.min(start + CHUNK_SIZE, tokens.size());
            while (!isCharacterBoundary(content, offsets[end])) {
                end--;
            }
            String chunk = decode(content, offsets[start], offsets[end]);
            while (encoding.countTokens(chunk.trim()) > CHUNK_SIZE) {
                do {
                    end--;
                } while (!isCharacterBoundary(content, offsets[end]));
                chunk = decode(content, offsets[start], offsets[end]);
            }
            if (end <= previousEnd) {
                start++;
                while (!isCharacterBoundary(content, offsets[start])) {
                    start++;
                }
                continue;
            }

            if (end < tokens.size()) {
                int punctuation = lastPunctuationIndex(chunk);
                if (punctuation > MIN_CHUNK_SIZE_CHARS) {
                    int punctuationOffset = offsets[start]
                            + chunk.substring(0, punctuation + 1).getBytes(StandardCharsets.UTF_8).length;
                    int punctuationEnd = Arrays.binarySearch(offsets, start + 1, end + 1, punctuationOffset);
                    if (punctuationEnd > previousEnd && punctuationEnd > start + chunkOverlap
                            && encoding.countTokens(chunk.substring(0, punctuation + 1).trim()) <= CHUNK_SIZE) {
                        end = punctuationEnd;
                        chunk = decode(content, offsets[start], offsets[end]);
                    }
                }
            }

            Assert.isTrue(++iterations <= MAX_NUM_CHUNKS, "Document exceeds the 5000-chunk splitting limit");
            if (chunk.trim().length() > MIN_CHUNK_LENGTH_TO_EMBED) {
                chunks.add(chunk.trim());
            }
            if (end == tokens.size()) {
                break;
            }
            previousEnd = end;
            start = Math.max(start + 1, end - chunkOverlap);
            while (!isCharacterBoundary(content, offsets[start])) {
                start++;
            }
        }
        return chunks;
    }

    private int[] tokenByteOffsets(IntArrayList tokens) {
        int[] offsets = new int[tokens.size() + 1];
        IntArrayList singleToken = new IntArrayList(1);
        singleToken.add(0);
        for (int tokenIndex = 0; tokenIndex < tokens.size(); tokenIndex++) {
            singleToken.set(0, tokens.get(tokenIndex));
            offsets[tokenIndex + 1] = offsets[tokenIndex] + encoding.decodeBytes(singleToken).length;
        }
        return offsets;
    }

    private boolean isCharacterBoundary(byte[] content, int offset) {
        return offset == content.length || (content[offset] & 0xC0) != 0x80;
    }

    private String decode(byte[] content, int start, int end) {
        return new String(content, start, end - start, StandardCharsets.UTF_8);
    }

    private int lastPunctuationIndex(String text) {
        int lastPunctuation = -1;
        for (char punctuation : PUNCTUATION_MARKS) {
            lastPunctuation = Math.max(lastPunctuation, text.lastIndexOf(punctuation));
        }
        return lastPunctuation;
    }
}
