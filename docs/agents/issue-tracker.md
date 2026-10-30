# Issue tracker: GitHub

Issues and specs live in GitHub Issues for `PeterTian0626/nba-game-master`:
https://github.com/PeterTian0626/nba-game-master/issues

Use connected GitHub tools when they support the operation; otherwise use the `gh` CLI. Target this repository explicitly rather than relying on an upstream or fork default.

## CLI readiness

Run `gh repo view PeterTian0626/nba-game-master --json nameWithOwner` before the first CLI operation. If authentication is required, have the user complete `gh auth login --hostname github.com`. After installing the CLI, open a new terminal if the current terminal cannot find `gh`.

## Conventions

- Create an issue: `gh issue create --repo PeterTian0626/nba-game-master --title "..." --body-file <file>`.
- Read an issue: `gh issue view <number> --repo PeterTian0626/nba-game-master --json number,title,body,labels,comments`.
- List issues: `gh issue list --repo PeterTian0626/nba-game-master --state open --json number,title,body,labels`. Add `--label` filters as needed.
- Comment: `gh issue comment <number> --repo PeterTian0626/nba-game-master --body-file <file>`.
- Change triage state: `gh issue edit <number> --repo PeterTian0626/nba-game-master --add-label "..." --remove-label "..."`. Use the mapping in `triage-labels.md`.
- Close an issue: post the explanation as a comment first, then run `gh issue close <number> --repo PeterTian0626/nba-game-master`.
- For multiline bodies, write the exact Markdown to a temporary UTF-8 file and pass `--body-file`.
- Publish to the issue tracker means create a GitHub issue here. Fetch a ticket means read its issue body and comments.

## Parent issues and blockers

Use native GitHub sub-issues and dependencies when available. With a supporting `gh` version, use `gh issue edit <parent> --add-sub-issue <child>` and `gh issue edit <child> --add-blocked-by <blocker>`, always with `--repo PeterTian0626/nba-game-master`.

If those features are unavailable, put `Part of #<parent>` and `Blocked by: #<number>, #<number>` at the top of the child body, and add children to a task list in the parent. Work becomes unblocked when every blocker is closed.

## Pull requests as a triage surface

**PRs as a request surface: no.**

## Wayfinding

- The map is a GitHub issue labeled `wayfinder:map`, containing Notes, Decisions-so-far, and Fog sections.
- Each ticket is a child issue labeled `wayfinder:<type>`, where the type is `research`, `prototype`, `grilling`, or `task`.
- The frontier consists of open, unassigned children with no open blockers, in map order.
- Claim a ticket by assigning it to the driving developer before starting work.
- Resolve by commenting with the answer, closing the ticket, and appending its result and link to the map's Decisions-so-far section.
