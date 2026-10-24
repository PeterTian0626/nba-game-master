# NBA Game Master

NBA Game Master is a Java/Spring Boot application for NBA conversation, team-specific trade evaluation, and retrieval-augmented generation (RAG) over bundled basketball analysis documents.

The AI entry point is the Spring component `NBAMasterApp`. The only application REST endpoint currently implemented is `GET /health`, which returns `ok`. Chat methods are called from Java and tests; there is no chat REST controller or frontend yet.

## Documentation

| Read this | For |
| --- | --- |
| [Architecture.md](Architecture.md) | Technology stack, startup, dependencies, and the purpose of every source/test file |
| [Agent.md](Agent.md) | Chat, memory, structured trade analysis, RAG, and development workflows |
| [Configuration and setup](docs/configuration.md) | OpenAI credentials/models, local startup, PostgreSQL/Supabase, and troubleshooting |
| [RAG knowledge base](docs/rag.md) | Markdown corpus, parsing, enrichment, ingestion, and retrieval |
| [Query rewriting](docs/query-rewriting.md) | Rewrite configuration, caching, fallback, and session inspection |
| [Testing and documentation checks](docs/testing.md) | Unit versus integration tests, commands, coverage gaps, and review evidence |

## Technology at a glance

Versions below come from [pom.xml](pom.xml), not the historical `dep_tree.txt`.

| Area | Implementation |
| --- | --- |
| Language/build | Java 17, Maven; wrapper distribution 3.9.14 |
| Application/web | Spring Boot 3.5.13, Spring MVC, embedded server |
| AI orchestration | Spring AI 1.1.5 `ChatClient`, advisors, RAG, Markdown reader, embedding/vector-store APIs |
| Model provider | OpenAI API; examples explicitly select `gpt-4.1-nano-2025-04-14` |
| Embeddings | OpenAI; documentation examples explicitly select `text-embedding-3-small` (1536 dimensions) |
| Retrieval storage | In-process `SimpleVectorStore` by default, or PostgreSQL with pgvector |
| Hosted database | Supabase PostgreSQL can provide the pgvector backend through JDBC |
| Conversation storage | Kryo 5.6.2 filesystem persistence, ten-message window |
| Other libraries | OpenAI Java SDK 4.0.0 for a standalone demo, springdoc 2.8.6, Lombok, JUnit 5/Mockito |

The application uses OpenAI's API, so a ChatGPT subscription alone does not configure it. See [model configuration](docs/configuration.md#models-and-effective-configuration) for the properties that select the API models.

## Quick start: in-memory retrieval

Prerequisites: JDK 17, access to Maven dependencies, and an OpenAI API key with access to the selected models. This mode skips JDBC auto-configuration but still calls OpenAI to embed the bundled corpus at startup.

From the repository root in PowerShell, set `OPENAI_API_KEY` in your environment, then run:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=docs --spring.config.additional-location=file:docs/examples/application-simple.yml"
```

On macOS/Linux, use `./mvnw` with the same arguments. If the Windows wrapper fails before Maven starts, use an installed `mvn` instead; see [troubleshooting](docs/configuration.md#troubleshooting).

In a second terminal:

```powershell
Invoke-RestMethod http://localhost:8080/health
```

Expected result: `ok`. This is a liveness response, not a database/model readiness check. Swagger UI is supplied by springdoc at `/swagger-ui/index.html`, with an OpenAPI description at `/v3/api-docs`; it does not expose the Java chat methods as HTTP endpoints.

For persistent retrieval, follow the [PostgreSQL/Supabase setup](docs/configuration.md#postgresql-and-supabase). Ingestion is an explicit startup action and can add duplicate documents when repeated.

## Development

Start with the [isolated unit test command](docs/testing.md#isolated-unit-tests) when changing application logic. Plain `mvn test` also discovers Spring context tests that may call OpenAI and a database during startup. The tagged chat end-to-end tests require `-Dnba.e2e.enabled=true`.

Build a jar with `mvn -DskipTests package` (or the Maven wrapper). `-DskipTests` skips test execution, not test compilation. No Docker setup, schema migrations, or CI workflow is checked in. Keep credentials in environment variables and never commit them.
