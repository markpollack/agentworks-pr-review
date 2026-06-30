# Step 2.3: RebaseStep — Learnings

**Completed**: 2026-04-08

## What Was Built

`RebaseStep` implementing `Step<PrContext, RebaseResult>` — executes git fetch, checkout, and rebase via `ProcessBuilder`. On conflict, collects conflicted files via `git diff --name-only --diff-filter=U`, then aborts the failed rebase to leave the repo clean.

## Key Decisions

1. **ProcessBuilder over JGit** — Direct git CLI is simpler and more workshop-readable. JGit would add a heavy dependency for a straightforward sequence of three git commands.

2. **Configurable working directory** — `workingDirectory(Path)` fluent setter allows tests to point at a temp directory. Default is `.` (current dir).

3. **stdout/stderr captured separately** — `redirectErrorStream(false)` so error messages are available for logging and `RebaseResult.message()`.

4. **Rebase abort on conflict** — Always runs `git rebase --abort` after collecting conflict files. Leaves the repo in a clean state for subsequent steps.

## Patterns

- `ProcessResult` inner record — lightweight (exitCode, stdout, stderr) avoids exposing `Process` internals
- Error case returns `RebaseResult` with error message rather than throwing — keeps workflow flow control simple
- `Thread.currentThread().interrupt()` on `InterruptedException` — preserves interrupt flag

## Test Approach

- Unit test points `workingDirectory` at `/nonexistent/repo` — verifies error handling path without needing a real git repo
- Name and type introspection tests for workflow registration

## Journal Integration

Deferred to a dedicated journal wiring step. The step currently logs via SLF4J.
