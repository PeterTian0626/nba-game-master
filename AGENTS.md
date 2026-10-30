# NBA Game Master

Read `Architecture.md` to locate the owner of a behavior and `Agent.md` for the application's chat, memory, trade-analysis, and RAG workflows.

Before running tests, read `docs/testing.md`. Start with isolated tests; full Spring context tests can call OpenAI and write to a database or vector store.

## Agent skills

### Issue tracker

Track issues and specs in GitHub Issues for `PeterTian0626/nba-game-master`. Read `docs/agents/issue-tracker.md` before creating, reading, or updating tickets.

### Triage labels

Use the five default triage labels. Read `docs/agents/triage-labels.md` before assigning or changing a ticket's triage state.

### Domain docs

Use a single root `GLOSSARY.md` and `docs/adr/`. Read `docs/agents/domain.md` when exploring domain behavior, discussing terminology, or recording architectural decisions.
