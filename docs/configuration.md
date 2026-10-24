# Configuration and setup

Return to [Readme.md](../Readme.md). Source wiring is described in [Architecture.md](../Architecture.md).

## Configuration sources

The repository ignores `src/main/resources/application.yml`, `application-local.yml`, and `.env`. They may exist on a developer's machine but are not available in a fresh clone. The inspected local YAML activates `local`, selects simple vectors, and enables optional ingestion. Those are local choices, not tracked application defaults.

The checked-in [simple](examples/application-simple.yml) and [pgvector](examples/application-pgvector.yml) examples contain environment placeholders only. Load one using `--spring.config.additional-location=file:...` and select `--spring.profiles.active=docs` to avoid automatically activating the ignored `local` profile. The `docs` profile has no custom Java behavior; it is simply an explicit profile choice. Base `application.yml`, if present, still participates; the additional example overrides the settings it declares. Command-line arguments override file settings.

Spring Boot does not automatically read this repository's `.env`. The SDK demo's `TestApiKey` helper does; this does not configure the Spring application.

## Models and effective configuration

| Concern | Property/value |
| --- | --- |
| Spring application API key | `spring.ai.openai.api-key`, examples use `${OPENAI_API_KEY}` |
| Chat model | `spring.ai.openai.chat.options.model` |
| Embedding model | `spring.ai.openai.embedding.options.model` |
| Explicit example chat model | `gpt-4.1-nano-2025-04-14` |
| Explicit example embedding model | `text-embedding-3-small`, default output dimension 1536 |
| Spring AI 1.1.5 chat fallback if unset | `gpt-4o-mini` |
| Spring AI 1.1.5 embedding fallback if unset | `text-embedding-ada-002` |

The fallback names were checked against the locally cached Spring AI 1.1.5 sources (`OpenAiChatProperties` / `OpenAiEmbeddingProperties`); they are not a recommendation to rely on implicit defaults. Both answer generation and rewriting use the same configured chat model. Keyword enrichment also uses the injected chat model.

The inspected ignored YAML places the intended chat model at `spring.chat.options.model`. Spring AI binds `spring.ai.openai.chat.options.model`, so that local setting does not select the model. The SDK demo does explicitly set `gpt-4.1-nano-2025-04-14` independently of Spring configuration.

The pinned chat snapshot is documented in [official OpenAI model documentation](https://developers.openai.com/api/docs/models/gpt-4.1-nano). OpenAI documents `text-embedding-3-small`'s default 1536-dimensional output in its [embedding guide](https://developers.openai.com/api/docs/guides/embeddings). These example selections make configuration explicit; they do not establish that an existing database was built with that embedding model. Before using an existing vector table, determine its original model and dimensions. Re-embed the corpus into a separately planned/rebuilt table when changing embedding models, even if both have 1536 dimensions.

The application uses API credentials. A ChatGPT web/app model selection or subscription does not set the API model or credentials here. Verify account access when running the examples; this documentation does not assert a particular account's model availability or pricing.

## In-memory setup

Set `OPENAI_API_KEY` in your shell/IDE environment before launching. Run from the repository root:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=docs --spring.config.additional-location=file:docs/examples/application-simple.yml"
```

Use `./mvnw` on Unix or an installed `mvn` as a wrapper alternative. The example sets:

- `app.vector-store.type=simple`.
- `app.rag.ingestion.enabled=false` to avoid a second, enriched copy of the corpus.
- `spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration`, because the JDBC starter otherwise attempts to configure a data source even in simple mode.
- Explicit OpenAI chat and embedding models and a required environment key.

`SimpleVectorStore` still loads/embeds documents at bean creation. Expect startup to require network/API access and incur embedding usage. Its vectors are lost on restart; conversation files are separate and can survive.

## PostgreSQL and Supabase

The application's database integration is standard PostgreSQL JDBC plus Spring AI `PgVectorStore`. Supabase is a hosted PostgreSQL provider referenced by the inspected local connection configuration. There is no Supabase Java SDK, REST client, Auth, Storage, Edge Function, or Supabase-specific vector RPC in the code.

1. Provision an appropriate PostgreSQL database with pgvector support. For Supabase, use its [database connection guidance](https://supabase.com/docs/guides/database/connecting-to-postgres) to choose an endpoint suitable for your network and server process, and its [pgvector extension guide](https://supabase.com/docs/guides/database/extensions/pgvector) for extension setup.
2. Set `NBA_DB_URL` to a PostgreSQL JDBC URL, `NBA_DB_USERNAME` to the database role, and `NBA_DB_PASSWORD` to its database password. The URL must start with `jdbc:postgresql://`, not a plain `postgresql://` URI. Follow your provider's TLS/certificate requirements; include the appropriate `sslmode` and certificate settings for that connection. Keep credentials outside tracked files.
3. Confirm the selected embedding model matches the corpus already stored, or start with an empty, deliberately chosen database/table. The factory hard-codes vector dimensions to 1536.
4. Ensure the role/schema setup permits initialization. In Spring AI 1.1.5, this factory's initialization attempts `CREATE EXTENSION IF NOT EXISTS` for `vector`, `hstore`, and `uuid-ossp`, plus table/index creation. Extensions may need provisioning by a database administrator; initialization must be able to find the installed extension objects through the connection's schema/search path.
5. Start with the checked-in pgvector example. It has ingestion disabled, so an empty database remains without corpus vectors until the separate ingestion step below.

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=docs --spring.config.additional-location=file:docs/examples/application-pgvector.yml"
```

The factory always uses schema `public`, table `vector_store`, cosine distance, HNSW, 1536 dimensions, schema initialization enabled, and maximum document batch size 10000. These values are Java builder settings, not bound `spring.ai.vectorstore.pgvector.*` options. There is no migration tool; startup schema initialization is not a migration strategy and does not convert an existing incompatible table.

To populate the corpus deliberately, launch with ingestion enabled:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=docs --spring.config.additional-location=file:docs/examples/application-pgvector.yml --app.rag.ingestion.enabled=true"
```

Wait for `RAG ingestion completed. ... documents added.` and confirm the expected data. Stop that process and return to the ingestion-disabled command for subsequent normal starts. The runner executes on every enabled startup, calls the keyword and embedding models, and does not deduplicate or remove old content. Use a deliberate cleanup/reindex procedure for updates; see [RAG ingestion](rag.md#ingestion-and-storage-behavior).

For a basic database check through your SQL client:

```sql
SELECT count(*) FROM public.vector_store;
SELECT extname FROM pg_extension WHERE extname IN ('vector', 'hstore', 'uuid-ossp');
```

A nonzero row count does not prove the model matches or the corpus is complete. No default row count is promised because Markdown parsing can produce multiple documents per file.

## Application switches

| Property | Default in code | Notes |
| --- | --- | --- |
| `app.vector-store.type` | `simple` if missing | Only `simple` and `pgvector` are implemented |
| `app.rag.ingestion.enabled` | Disabled unless `true` | Separate enriched startup ingestion; simple factory still loads documents |
| `app.rag.query-rewriter.enabled` | `true` | Model rewriting for RAG retrieval |
| `app.rag.query-rewriter.min-query-length` | `0` | Inclusive Java string length lower bound; blank input is still rejected |
| `app.rag.query-rewriter.max-query-length` | `4000` | Inclusive upper bound; out-of-range text passes through |
| `app.rag.query-rewriter.cache-capacity` | `1000` | Successful rewrite LRU capacity; zero disables retention |
| `app.rag.query-rewriter.session-capacity` | `1000` | Latest effective query per session; zero disables retention |

No configurable application property currently controls the memory directory/window, keyword count, retriever top-K/threshold, or pgvector builder values. Change the owning Java code to change those behaviors. The default HTTP port is Spring Boot's 8080 unless `server.port` is set.

## Standalone demos

Run demo main classes from the IDE with the project dependencies on the classpath:

- `SDKAiInvoke`: directly uses the OpenAI SDK Responses API. `TestApiKey` checks `OPENAI_API_KEY`, then JVM property `openai.apiKey`, then `OPENAI_API_KEY` in the working directory's `.env`. This bypasses Spring, memory, retrieval, and ingestion.
- `SpringAiInvoke`: starts the full Spring context with web mode disabled, then uses a bare `ChatClient.Builder`. Supply the same Spring configuration arguments as for the application; store initialization and optional ingestion still occur. It does not call `NBAMasterApp` or install its NBA prompt/advisors.
- `SpringAiInvoke2` and `LangChain4jInvoke`: commented-out examples, not runnable application flows.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Windows wrapper: `Cannot index into a null array` / `Cannot start maven from wrapper` | The current `mvnw.cmd` indexes `(Get-Item $MAVEN_M2_PATH).Target[0]`; some Windows PowerShell directory objects expose a null target. Use an installed Maven (`mvn`) or IDE Maven runner. This docs change does not repair the wrapper. |
| Offline build cannot resolve Spring Boot 3.5.13 parent | The required POM is not cached for the selected Maven repository. Resolve dependencies with network access before using `-o`; `dep_tree.txt` cannot supply artifacts. |
| Startup says a JDBC URL/driver is missing in simple mode | Use the simple example's explicit data source auto-configuration exclusion, or configure a real data source intentionally. |
| OpenAI key missing despite `.env` existing | Export/set the environment value used by `${OPENAI_API_KEY}`; only the SDK helper reads `.env`. |
| The wrong chat model is used | Verify `spring.ai.openai.chat.options.model`, not `spring.chat.options.model`; check higher-precedence arguments/environment settings. |
| PostgreSQL extension/table/index permission failure | Check role privileges, installed extensions and search path, and managed-database initialization policy. Schema initialization is always enabled in the current factory. |
| Vector dimension mismatch or poor results after a model change | Verify both embedding model identity and 1536-dimensional output; plan a corpus reindex instead of mixing models. |
| Empty pgvector results | Confirm `app.vector-store.type=pgvector`, connection/table, and whether ingestion has ever run against that database. |
| Duplicate corpus results | Check repeated enabled ingestion and simple mode's independent initial load; there are no stable ingestion IDs/deduplication. |
| Chat history disappears after restart | Check working directory and persistence of `tmp/chat-memory`; rewrite cache and simple vectors are independently transient. |
| `/health` succeeds but chat fails | The endpoint returns a constant; it does not check the model, embeddings, retrieval contents, or subsequent database connectivity. |
