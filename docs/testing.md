# Testing and documentation reliability

Return to [Readme.md](../Readme.md). File-by-file test responsibilities are listed in [Architecture.md](../Architecture.md#tests).

## Isolated unit tests

Run from the repository root with JDK 17 and resolved Maven dependencies:

```powershell
.\mvnw.cmd "-Dtest=NBAMasterAppUnitTest,QueryRewriterTest,ReReadingAdvisorTest,MyLoggerAdvisorTest,FileBasedChatMemoryRepositoryTest" test
```

Use `mvn` if the Windows wrapper encounters the issue described in [troubleshooting](configuration.md#troubleshooting), or `./mvnw` on Unix. These five classes do not start the full application context and use mocked model/store calls or local filesystem operations, so they do not need OpenAI/database credentials. `NBAMasterAppUnitTest` uses a lightweight Spring context to check retrieval-property binding. Dependency downloads can still need network access.

The ingestion suite is also isolated from external services:

```powershell
mvn "-Dtest=NbaAppDocumentLoaderTest,MyTokenTextSplitterTest,PgVectorDocumentIngestionTest,RagDocumentIngestionRunnerTest,RagDocumentIngestionRepositoryTest" test
```

It covers unchanged-source skipping before parsing/model calls, pipeline/content changes (including the overlap migration), stable chunk identities, metadata retention, split-before-enrich ordering, single population of the simple store, and rollback/retry boundaries. Splitter tests use the real tokenizer to verify shared context, zero/high overlap, Unicode boundaries, source coverage, per-document isolation, token limits, and the bundled corpus. Transaction managers and JDBC calls are mocked: this does not establish live PostgreSQL rollback, RLS permissions, or provider connectivity. A deliberate live run must validate those separately.

`NBAMasterAppUnitTest` constructs the production app, which creates/writes filesystem conversation state even with a mocked model. RAG cases use random IDs while basic/structured cases use fixed IDs; repeated runs may encounter persisted history. The repository-specific tests use `@TempDir` instead.

The most important orchestration assertion is `doChatWithRagSearchesRewrittenQueryAndAnswersOriginalQuestion`: it verifies that similarity search receives the rewrite while the final answer prompt contains the original question and retrieved text. It does not evaluate semantic relevance or live model answer quality.

RAG policy tests check that empty retrieval preserves unrelated, current-data, and conceptual NBA questions, and that retrieved material is supplemental rather than the exclusive evidence source. They inspect shared NBA-scope and current-fact verification instructions in the system prompt, including basic and structured chat, and preserve retrieval-error propagation and cutoff configuration coverage. They use a mocked answer model; they do not establish live topic detection, factual freshness, tool execution, model compliance, or calibration of the similarity cutoff. Current-data tools are not implemented by this change.

## Spring context and live tests

`mvn test` discovers more than isolated unit tests. The POM does not separate integration tests into a Failsafe phase/profile. Full-context tests can instantiate vector stores and run optional ingestion before the test body executes.

| Class | Current behavior |
| --- | --- |
| `NbaGameMasterApplicationTests` | Loads the full context; startup dependencies apply |
| `PgVectorVectorStoreConfigTest` | Loads the full context, performs a search, and logs results without assertions; does not itself select pgvector |
| `NBAMasterAppTest` | Full-context live chat tests, gated by `nba.e2e.enabled=true` and tagged `e2e` |

The pgvector test's field name does not prove the selected backend; with a single simple store, `@Resource` can resolve by type. Explicitly choose pgvector for a database-backed run.

The following loader command runs isolated local parsing tests; the supplied Spring properties are not used because this test does not start a Spring context:

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

- The loader tests cover local parsing and failure handling, not retrieval relevance.
- The pgvector test does not assert backend selection or result relevance, and its example inserts are commented out.
- The live memory test performs two calls without checking that the second answer remembers the user's team.
- The logger's streaming-related test checks private logging helpers; it does not exercise the complete streaming chain.
- Keyword generation quality, concurrent ingestion, concurrent memory-file access, and HTTP controllers have no dedicated tests.
- Mocked trade conversion tests verify field mapping, not factual correctness or guaranteed provider-native structured output.

These are existing gaps, not claims that these scenarios were validated by the documentation work.

## Second-pass documentation review

When changing this knowledge base, make a separate pass after the initial draft:

1. Compare versions/dependencies with `pom.xml` and `.mvn/wrapper/maven-wrapper.properties`, not historical output in `dep_tree.txt`.
2. Cross-check every class/method/property and default against its implementation. For Spring AI defaults that the code inherits, check the pinned 1.1.5 library sources rather than assuming the latest documentation matches.
3. Check the architecture map against all tracked production/test Java files and all five corpus files. Distinguish active code and commented demos, and include only tracked repository paths in the documentation.
4. Trace startup, both vector-store paths, ingestion, and all three chat methods. Verify examples do not imply a chat HTTP API, offline simple embeddings, automatic pgvector population, or credential configuration shared between the SDK demo and Spring application.
5. Resolve every relative Markdown link and heading anchor. Check example YAML parses, uses the correct model prefix, contains only environment placeholders, and selects exactly one store.
6. Review claims against test assertions, not test names. Record commands attempted and any execution limits without implying unrun tests passed.

### Validation scope

The knowledge base documents tracked source, tests, build settings, corpus files, and checked-in configuration examples. Validate repository links and file coverage against `git ls-files`; keep private configuration and workstation artifacts outside the documentation.

Static documentation checks do not establish live credentials, endpoint connectivity, model access, or passing Java tests. Report those checks separately from application test execution.
