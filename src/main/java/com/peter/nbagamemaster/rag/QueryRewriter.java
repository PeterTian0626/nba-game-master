package com.peter.nbagamemaster.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class QueryRewriter {

    private static final String REWRITE_PROMPT = """
            Rewrite the query below for semantic search over NBA analysis documents in a {target}.
            Preserve the user's intent, player and team names, dates, trade details,
            and constraints. Remove conversational filler. Do not answer the question,
            invent facts, or resolve ambiguous references without evidence.
            Keep the query in its original language and return only the rewritten query.

            Original query:
            {query}

            Rewritten query:
            """;

    private final QueryTransformer queryTransformer;
    private final boolean enabled;
    private final int minQueryLength;
    private final int maxQueryLength;
    private final Map<String, String> rewriteCache;
    private final Map<String, String> sessionQueries;

    public QueryRewriter(
            ChatModel openAIChatModel,
            @Value("${app.rag.query-rewriter.enabled:true}") boolean enabled,
            @Value("${app.rag.query-rewriter.min-query-length:0}") int minQueryLength,
            @Value("${app.rag.query-rewriter.max-query-length:4000}") int maxQueryLength,
            @Value("${app.rag.query-rewriter.cache-capacity:1000}") int cacheCapacity,
            @Value("${app.rag.query-rewriter.session-capacity:1000}") int sessionCapacity
    ) {
        Assert.notNull(openAIChatModel, "chatModel cannot be null");
        Assert.isTrue(minQueryLength >= 0, "minQueryLength cannot be negative");
        Assert.isTrue(maxQueryLength >= minQueryLength, "maxQueryLength must be at least minQueryLength");
        Assert.isTrue(cacheCapacity >= 0, "cacheCapacity cannot be negative");
        Assert.isTrue(sessionCapacity >= 0, "sessionCapacity cannot be negative");
        this.queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(openAIChatModel))
                .promptTemplate(new PromptTemplate(REWRITE_PROMPT))
                .build();
        this.enabled = enabled;
        this.minQueryLength = minQueryLength;
        this.maxQueryLength = maxQueryLength;
        this.rewriteCache = boundedCache(cacheCapacity);
        this.sessionQueries = boundedCache(sessionCapacity);
    }

    public String rewrite(String prompt) {
        Assert.hasText(prompt, "prompt cannot be null or blank");
        if (!this.enabled || prompt.length() < this.minQueryLength || prompt.length() > this.maxQueryLength) {
            return prompt;
        }

        String cachedQuery = this.rewriteCache.get(prompt);
        if (cachedQuery != null) {
            return cachedQuery;
        }

        try {
            Query query = new Query(prompt);
            Query transformedQuery = this.queryTransformer.transform(query);
            if (transformedQuery != null && transformedQuery != query && StringUtils.hasText(transformedQuery.text())) {
                String rewrittenQuery = transformedQuery.text().strip();
                this.rewriteCache.put(prompt, rewrittenQuery);
                return rewrittenQuery;
            }
        } catch (RuntimeException exception) {
            log.warn("Query rewriting failed; using the original query", exception);
        }
        return prompt;
    }

    public String rewriteAndSave(String sessionId, String prompt) {
        Assert.hasText(sessionId, "sessionId cannot be null or blank");
        String rewrittenQuery = rewrite(prompt);
        this.sessionQueries.put(sessionId, rewrittenQuery);
        return rewrittenQuery;
    }

    public String getLastRewrittenQuery(String sessionId) {
        Assert.hasText(sessionId, "sessionId cannot be null or blank");
        return this.sessionQueries.get(sessionId);
    }

    public String doQueryRewrite(String prompt) {
        return rewrite(prompt);
    }

    private static Map<String, String> boundedCache(int capacity) {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > capacity;
            }
        });
    }
}
