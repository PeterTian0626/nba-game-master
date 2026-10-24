# Architecture and repository map

Return to [Readme.md](Readme.md). For behavioral details, read [Agent.md](Agent.md); for operational configuration, read [docs/configuration.md](docs/configuration.md).

## System boundary

This is one Maven module, with a single Spring Boot application under `com.peter.nbagamemaster`. Packages organize responsibilities; they are not separately deployed services. `NBAMasterApp` composes Spring AI advisors around one injected `ChatModel`. `QueryRewriter` builds a separate client using the same model bean. Both clients therefore share model configuration, while only the answer client has conversation memory and rereading advisors.

The pinned Spring AI OpenAI integration uses the Chat Completions API by default (`/v1/chat/completions`), while the standalone `SDKAiInvoke` demo uses the Responses API. Embeddings use the separate embedding API. The SDK dependency does not mean the main application uses the SDK demo's client or model setting.

```mermaid
flowchart TD
    Caller[Java caller or test] --> App[NBAMasterApp]
    HTTP[HTTP client] --> Health[HealthController: GET /health]
    App --> Answer[Spring AI answer ChatClient]
    Answer <--> Memory[MessageWindowChatMemory]
    Memory <--> Disk[Filesystem conversation persistence]
    App --> Rewrite[QueryRewriter: separate ChatClient]
    Rewrite --> OpenAI[OpenAI chat API]
    Answer --> OpenAI
    App --> RAG[RetrievalAugmentationAdvisor]
    RAG --> Store[Selected VectorStore]
    Store --> Embeddings[OpenAI embedding API]
    Store --> Simple[SimpleVectorStore: process memory]
    Store --> PG[PgVectorStore: PostgreSQL / Supabase]
    Corpus[Classpath documents/*.md] --> Loader[NbaAppDocumentLoader]
    Loader --> Simple
    Loader --> Ingest[Optional RagDocumentIngestionRunner]
    Ingest --> Enricher[MyKeywordEnricher: chat model]
    Enricher --> Store
```

`SimpleVectorStore` and `PgVectorStore` are alternatives. The diagram's retrieval path applies to `doChatWithRag`; basic and structured chat do not retrieve documents. Retrieval and model calls are synchronous in the application API. There are no application tool calls, NBA data feeds, autonomous planning loops, or background agents.

## Startup and dependency wiring

1. `NbaGameMasterApplication` starts Spring Boot and scans the application package.
2. Spring AI auto-configures OpenAI chat/embedding models from `spring.ai.openai.*`. Spring Boot's JDBC starter also triggers data source auto-configuration unless explicitly excluded.
3. Exactly one vector-store factory should match `app.vector-store.type`:
   - Missing or `simple`: bean `vectorStore`, which immediately loads and embeds Markdown.
   - `pgvector`: bean `pgVectorVectorStore`, which initializes the database schema through Spring's bean lifecycle; it does not load documents by itself.
   - Any other value: neither factory matches, so required vector-store injection fails.
4. `NBAMasterApp` constructs its filesystem memory repository and answer client. Its `@Resource` field is named `nbaAppVectorStore`, but neither factory uses that name; injection relies on the single matching `VectorStore` type. Adding multiple stores requires explicit wiring changes.
5. If `app.rag.ingestion.enabled=true`, `RagDocumentIngestionRunner` loads, enriches, and adds the corpus after context initialization. In simple mode, this happens in addition to the factory's initial load.

The pgvector factory returns the `VectorStore` interface, but the concrete instance implements Spring initialization. Its configured schema initialization is performed when managed as a Spring bean. Building a store manually outside Spring does not itself run that lifecycle callback.

## Repository-level files and directories

| Path | Purpose |
| --- | --- |
| `pom.xml` | Single-module dependency/build definition: Java 17, Spring Boot 3.5.13, Spring AI BOM 1.1.5, compilation/packaging plugins |
| `mvnw`, `mvnw.cmd` | Unix/Windows Maven wrapper launchers |
| `.mvn/wrapper/maven-wrapper.properties` | Wrapper 3.3.4 configuration selecting Maven 3.9.14; downloads a distribution when needed |
| `.gitignore` | Excludes local credentials/configuration, generated output, runtime state, and IDE metadata |
| `.gitattributes` | Git text/line-ending handling |
| `dep_tree.txt` | Historical dependency-tree output; contains Spring AI 1.1.4, so it is not authoritative for the current build |
| `Readme.md`, `Architecture.md`, `Agent.md` | Documentation entry point, structural map, and agent/workflow guide |
| `docs/configuration.md` | Setup, properties, models, Supabase/JDBC, troubleshooting |
| `docs/rag.md`, `docs/query-rewriting.md` | Corpus/ingestion/retrieval guide and rewrite internals |
| `docs/testing.md` | Test classification, commands, limitations, and documentation verification |
| `docs/examples/application-simple.yml` | Credential-free example that selects in-memory vectors and excludes JDBC auto-configuration |
| `docs/examples/application-pgvector.yml` | Credential-free JDBC/pgvector example with explicit OpenAI models |

## Production Java files

All paths below are relative to `src/main/java/com/peter/nbagamemaster/`.

| File | Responsibility |
| --- | --- |
| `NbaGameMasterApplication.java` | Spring Boot `main` entry point and component scan root |
| `controller/HealthController.java` | `GET /health` returning the literal string `ok`; no dependency probes |
| `app/NBAMasterApp.java` | NBA system prompt; basic chat; structured `TeamTradeEvaluation` record; RAG chat; ten-message persisted memory; per-request conversation ID |
| `advisor/ReReadingAdvisor.java` | Repeats the user's question with `Read the question again:` before forwarding; customizable template/order, default order 0 |
| `advisor/MyLoggerAdvisor.java` | Logs request/response text at INFO; implements synchronous and streaming advisor interfaces, order 0 |
| `advisor/package-info.java` | Package-wide Spring non-null annotations |
| `chatMemory/FileBasedChatMemoryRepository.java` | Spring AI `ChatMemoryRepository` implementation with Kryo serialization, URL-safe Base64 conversation filenames, list/read/replace/delete operations |
| `rag/NbaAppDocumentLoader.java` | Reads `classpath:documents/*.md`, configures Markdown parsing, attaches `filename` metadata, returns Spring AI documents |
| `rag/NbaAppVectoreStoreConfig.java` | Default/simple store factory; embeds the corpus at bean creation. `Vectore` is the current filename/class spelling |
| `rag/PgVectorVectorStoreConfig.java` | Manual JDBC pgvector factory; 1536 dimensions, cosine distance, HNSW index, `public.vector_store`, schema initialization, batch limit 10000 |
| `rag/RagDocumentIngestionRunner.java` | Conditional startup ingestion: load Markdown, enrich metadata, add to the selected vector store, log document count |
| `rag/MyKeywordEnricher.java` | Uses the injected chat model and Spring AI `KeywordMetadataEnricher` to request five keywords per document |
| `rag/QueryRewriter.java` | NBA search-query transformer; length gates, bounded synchronized LRU maps for successful rewrites/latest session searches, graceful model-error fallback |
| `demo/invoke/SDKAiInvoke.java` | Standalone OpenAI Java SDK Responses API example; explicitly selects `gpt-4.1-nano-2025-04-14`, prints the first text output |
| `demo/invoke/TestApiKey.java` | Resolves the standalone SDK demo's API key; supports environment and JVM property configuration |
| `demo/invoke/SpringAiInvoke.java` | Starts the complete application context without a web server, obtains a bare `ChatClient.Builder`, and calls a sample prompt; startup store/ingestion work still applies |
| `demo/invoke/SpringAiInvoke2.java` | Commented-out `CommandLineRunner` example; no active component/class |
| `demo/invoke/LangChain4jInvoke.java` | Entirely commented-out alternative integration; LangChain4j dependency is also commented out in the POM |

`spring-ai-advisors-vector-store` is declared, but the implemented RAG path uses `RetrievalAugmentationAdvisor` from `spring-ai-rag`. The pgvector starter is commented out; the active manual factory uses `spring-ai-pgvector-store`, JDBC, and the PostgreSQL driver. Changing `spring.ai.vectorstore.pgvector.*` does not reconfigure this factory's hard-coded builder values.

## Knowledge corpus

The five Markdown resources are data sent through the embedding/RAG pipeline, not repository developer documentation. Each file's role is listed in [docs/rag.md](docs/rag.md#bundled-corpus). Files in root or `docs/` are outside the loader's resource pattern and are not ingested.

## Tests

All paths below are relative to `src/test/java/com/peter/nbagamemaster/`.

| File | Purpose and dependency boundary |
| --- | --- |
| `NbaGameMasterApplicationTests.java` | Full Spring context smoke test; startup can invoke model/database services |
| `app/unit/NBAMasterAppUnitTest.java` | Mocked chat/rewriter/store tests for basic text, record conversion, and retrieval of the rewritten query while answering the original question; writes local memory files |
| `app/NBAMasterAppTest.java` | Full-context live chat/memory, trade-analysis, and RAG tests; gated by JVM property `nba.e2e.enabled=true`, tagged `e2e` |
| `advisor/ReReadingAdvisorTest.java` | Verifies question augmentation and preservation of the system message without a Spring context |
| `advisor/MyLoggerAdvisorTest.java` | Verifies synchronous logging/returned response; streaming-related test inspects private logging helpers rather than running the full streaming chain |
| `chatMemory/FileBasedChatMemoryRepositoryTest.java` | Temporary-directory persistence across repository instances and conversation deletion |
| `rag/QueryRewriterTest.java` | Mocked rewrite, prompt preservation, caching/LRU, fallback/retry, validation, and bounded session storage tests |
| `rag/NbaAppDocumentLoaderTest.java` | Full-context loader smoke test; only calls `loadMarkdowns()` and currently has no assertions |
| `rag/PgVectorVectorStoreConfigTest.java` | Full-context similarity-search smoke test (`topK=2`); logs results with no assertions and does not force pgvector selection |

See [testing](docs/testing.md) before choosing a command: class names alone do not indicate whether a test is isolated from external services.

## Persistence and limitations

| State | Location/lifetime | Current caveats |
| --- | --- | --- |
| Conversation messages | Filesystem persistence configured in `NBAMasterApp`; survives restart if retained | Local files, no TTL, no file locking/atomic replacement, no application-level encryption or cross-instance coordination |
| Rewrite cache/latest session searches | Process memory; evicted by capacity or restart | Exact input keys, shared rewrite cache across sessions, no follow-up resolution using history |
| Simple document vectors | Process memory | Re-embedded on every startup; no save/load is called |
| pgvector document vectors | PostgreSQL `public.vector_store` | No migration tooling, corpus versioning, deduplication, or stale-document cleanup |

There is no application authentication, authorization, or mapping of conversation IDs to users. Structured output is a Java conversion contract without additional domain validation. Model/retrieval failures beyond rewriting generally propagate. These are implementation boundaries to account for before adding an HTTP chat API or deploying multiple instances.
