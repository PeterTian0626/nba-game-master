# Testing and documentation reliability

Return to [Readme.md](../Readme.md). File-by-file test responsibilities are listed in [Architecture.md](../Architecture.md#tests).

## Isolated unit tests

Run from the repository root with JDK 17 and resolved Maven dependencies:

```powershell
.\mvnw.cmd "-Dtest=NBAMasterAppUnitTest,QueryRewriterTest,ReReadingAdvisorTest,MyLoggerAdvisorTest,FileBasedChatMemoryRepositoryTest" test
```

Use `mvn` if the Windows wrapper encounters the issue described in [troubleshooting](configuration.md#troubleshooting), or `./mvnw` on Unix. These five classes do not start a Spring context and use mocked model/store calls or local filesystem operations, so they do not need OpenAI/database credentials. Dependency downloads can still need network access.

`NBAMasterAppUnitTest` constructs the production app, which creates/writes the working directory's `tmp/chat-memory` even with a mocked model. One RAG case uses a random ID while basic/structured cases use fixed IDs; repeated runs may encounter persisted history. The repository-specific tests use `@TempDir` instead.

The most important orchestration assertion is `doChatWithRagSearchesRewrittenQueryAndAnswersOriginalQuestion`: it verifies that similarity search receives the rewrite while the final answer prompt contains the original question and retrieved text. It does not evaluate semantic relevance or live model answer quality.

## Spring context and live tests

`mvn test` discovers more than isolated unit tests. The POM does not separate integration tests into a Failsafe phase/profile. Full-context tests can instantiate vector stores and run optional ingestion before the test body executes.

| Class | Current behavior |
| --- | --- |
| `NbaGameMasterApplicationTests` | Loads the full context; startup dependencies apply |
| `NbaAppDocumentLoaderTest` | Loads the full context, then calls the loader without assertions |
| `PgVectorVectorStoreConfigTest` | Loads the full context, performs a search, and logs results without assertions; does not itself select pgvector |
| `NBAMasterAppTest` | Full-context live chat tests, gated by `nba.e2e.enabled=true` and tagged `e2e` |

The loader's test annotation does not isolate Markdown parsing from embeddings or the database: startup happens first. The pgvector test's field name does not prove the selected backend; with a single simple store, `@Resource` can resolve by type. Explicitly choose pgvector for a database-backed run.

For a loader smoke run using the simple example (still performs startup embeddings):

```powershell
.\mvnw.cmd "-Dtest=NbaAppDocumentLoaderTest" "-Dspring.profiles.active=docs" "-Dspring.config.additional-location=file:docs/examples/application-simple.yml" test
```

For a deliberately configured pgvector search run, set the environment variables documented in [configuration](configuration.md#postgresql-and-supabase) and populate the test database as intended:

```powershell
.\mvnw.cmd "-Dtest=PgVectorVectorStoreConfigTest" "-Dspring.profiles.active=docs" "-Dspring.config.additional-location=file:docs/examples/application-pgvector.yml" test
```

For the live chat tests, set `OPENAI_API_KEY` and opt in explicitly:

```powershell
.\mvnw.cmd "-Dtest=NBAMasterAppTest" "-Dnba.e2e.enabled=true" "-Dspring.profiles.active=docs" "-Dspring.config.additional-location=file:docs/examples/application-simple.yml" test
```

The `e2e` tag is descriptive; the system-property condition is the actual opt-in gate in this repository. Live runs can use paid model APIs, persist conversations, and, with ingestion enabled, write corpus vectors. Use a deliberate development/test database rather than assuming isolation from production.

## Coverage limits

- The loader test does not assert corpus presence, filenames, excluded blocks, segmentation, or error behavior.
- The pgvector test does not assert backend selection or result relevance, and its example inserts are commented out.
- The live memory test performs two calls without checking that the second answer remembers the user's team.
- The logger's streaming-related test checks private logging helpers; it does not exercise the complete streaming chain.
- There are no dedicated ingestion-runner or keyword-enricher tests, concurrency tests for memory files, or HTTP controller tests.
- Mocked trade conversion tests verify field mapping, not factual correctness or guaranteed provider-native structured output.

These are existing gaps, not claims that these scenarios were validated by the documentation work.

## Second-pass documentation review

When changing this knowledge base, make a separate pass after the initial draft:

1. Compare versions/dependencies with `pom.xml` and `.mvn/wrapper/maven-wrapper.properties`, not historical output under `dep_tree.txt` or `target/`.
2. Cross-check every class/method/property and default against its implementation. For Spring AI defaults that the code inherits, check the pinned 1.1.5 library sources rather than assuming the latest documentation matches.
3. Check the architecture map against all tracked production/test Java files and all five corpus files. Distinguish active code, commented demos, and ignored machine-specific configuration.
4. Trace startup, both vector-store paths, ingestion, and all three chat methods. Verify examples do not imply a chat HTTP API, offline simple embeddings, automatic pgvector population, or automatic `.env` loading.
5. Resolve every relative Markdown link and heading anchor. Check example YAML parses, uses the correct model prefix, contains only environment placeholders, and selects exactly one store.
6. Review claims against test assertions, not test names. Record commands attempted and any execution limits without implying unrun tests passed.

### Review evidence for this documentation update

The initial documentation was reviewed against all tracked Java source/test files, the POM, wrapper properties, corpus, and redacted local configuration. The completed second pass verified coverage of all 27 tracked Java files and all five corpus files, resolved all 47 relative Markdown links/anchors, and checked fenced blocks and trailing whitespace across seven documentation pages. Both example YAML files parsed with the cached SnakeYAML library and passed assertions for model-property paths, environment placeholders, store selection, disabled ingestion, and the simple example's JDBC exclusion. `git diff --check` also passed for the tracked edit. Key corrections captured include:

- Spring AI's effective model property is `spring.ai.openai.chat.options.model`; the inspected ignored configuration's `spring.chat.options.model` is ineffective.
- Spring AI 1.1.5 defaults to `gpt-4o-mini` for chat and `text-embedding-ada-002` for embeddings; example files pin explicit models instead.
- Simple mode still embeds at startup, and enabled optional ingestion adds another corpus load; pgvector's factory initializes schema without populating data.
- JDBC auto-configuration must be handled even when simple vectors are selected; the simple example excludes it.
- The referenced loader test has no assertions and loads the full context; it is not an isolated loader correctness test.
- The main Spring AI integration defaults to Chat Completions; the standalone SDK demo uses Responses. Their model/credential settings are independent.
- Ingestion can fail startup on enrichment/store errors, while caught loader I/O failures can instead leave an empty/partial corpus.

Test execution was attempted with the isolated-class selection above. The Windows wrapper failed before Maven launched with `Cannot index into a null array`. A direct invocation of an existing Maven 3.9.14 installation in offline mode then failed to resolve the uncached Spring Boot 3.5.13 parent POM. Consequently, no Java tests ran during this documentation update. No live OpenAI calls, database ingestion, or application startup was used as validation. The examples' static checks do not establish live credentials, endpoint connectivity, or model access.
