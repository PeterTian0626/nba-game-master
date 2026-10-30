# Domain docs

This is a single-context project. Domain terminology belongs in the root `GLOSSARY.md`; architectural decision records belong in `docs/adr/`.

## Reading rules

Before exploring domain behavior, read `GLOSSARY.md` if it exists and the ADRs relevant to the area being changed. Use `Architecture.md` and `Agent.md` for the existing application architecture and workflows.

If the glossary or ADR directory is absent, proceed silently. Create them when domain modeling resolves a term or an architectural decision; setup does not create placeholder documents.

## Vocabulary and decisions

Use the glossary's terms in code discussions, issues, tests, and refactor proposals. When a concept is missing, check whether an existing project term covers it before proposing a new one.

Record architectural decisions as numbered Markdown files such as `docs/adr/0001-<decision>.md`. Surface conflicts with an existing ADR explicitly before proposing a different approach.
