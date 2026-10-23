# RAG query rewriting

The application is a Spring Boot 3 / Java 17 service using Spring AI 1.1.5.
`NBAMasterApp` provides basic chat, structured trade analysis, and RAG chat.
Its default advisors reread the question and maintain a ten-message window
through `FileBasedChatMemoryRepository`. RAG also adds request/response logging
and document retrieval. Markdown documents are embedded into either the simple
in-memory vector store or PostgreSQL/pgvector. Optional ingestion enriches
document metadata with keywords.

## Request flow

1. `QueryRewriter.rewriteAndSave(sessionId, message)` uses a separate ChatClient
   and Spring AI's `RewriteQueryTransformer` to produce an NBA search query.
   This client has no chat memory or rereading advisors.
2. `RetrievalAugmentationAdvisor` searches the vector store with the rewritten
   text. Its single retrieval runs synchronously without creating a thread pool
   for each request.
3. The answer model receives the original question augmented with retrieved
   documents. The rewritten search text does not replace the user's question.

Rewrites preserve names, dates, constraints, and the original language. They
do not use conversation history to resolve follow-up references. Session lookup
stores the latest effective search text for inspection; it is not chat memory.

## Configuration

All properties have defaults and can be set in application configuration.

| Property | Default | Behavior |
| --- | --- | --- |
| `app.rag.query-rewriter.enabled` | `true` | Enable model-based rewriting. |
| `app.rag.query-rewriter.min-query-length` | `0` | Minimum input length in Java characters, inclusive. |
| `app.rag.query-rewriter.max-query-length` | `4000` | Maximum input length, inclusive. Longer queries pass through unchanged. |
| `app.rag.query-rewriter.cache-capacity` | `1000` | Maximum cached successful rewrites; zero disables caching. |
| `app.rag.query-rewriter.session-capacity` | `1000` | Maximum retained session queries; zero disables storage. |

Both stores are thread-safe, in-memory, and evict least recently used entries.
They reset when the application restarts. Cache keys are exact input strings;
concurrent misses can perform more than one model call. Disabled rewriting,
out-of-range queries, model errors, and blank model output use the original text.
Failed and blank rewrites are not cached, so later requests can retry.
Null or blank queries/session IDs and invalid configuration are rejected.
