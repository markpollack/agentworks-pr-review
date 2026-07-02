# ROADMAP — AI Conflict-Resolution + Always-Review

*Created 2026-07-01 · From `DESIGN-ai-conflict-resolution.md` (DD-1..10). Forge form. Order: **C (DD-10) → B (DD-1..9)**, each validated before generalizing (A).*

> **Principle:** implement the specific reviewer behavior and *show it works* (against PR #1's real `CLAUDE.md` conflict) before generalizing to the product. Each stage has a validation gate (.K) run via `~/scripts/claude-run.sh` on `markpollack/agent-experiment`.

## Stage 1 — Always review the diff (DD-10) [C] — ✅ DONE
*The code review must not depend on mergeability. A rebase/build failure sets merge-readiness; it does not skip the review.*
*Validated on PR #1 (real `CLAUDE.md` conflict): review now runs + appears alongside merge-readiness=FAIL. See `plans/learnings/step-cr-1.K-always-review.md`.*

### Step 1.1 — Run the assess on both build-gate branches
- **Entry:** current pipeline `onPass(assessAndReport)/onFail(earlyReport)`; validation baseline = v10 (PR #1 conflict → early report, **no review**).
- **Work:** extract `assessAndReport` into a factory; use it on **both** `onPass` and `onFail`. Retire `earlyReport`. (DSL reuse is safe — `generateReport` is already shared across quality branches.)
- **Exit:** `./mvnw -q compile` + `verify` green.

### Step 1.K — Validate: review appears on conflict
- Run PR #1 (the `CLAUDE.md` conflict). **Expect:** report now contains the KB-consult code review **and** a merge-readiness = FAIL/needs-rebase; exit 0.
- Learnings → `plans/learnings/`; commit.
- *(Optional 1.2 — split the report into a "Code Review" section + a "Merge Readiness" section if the combined verdict reads confusingly. Decide after 1.K.)*

## Stage 2 — AI conflict resolution (DD-1..9) [B]
*Resolve rebase conflicts with the agent instead of escalating every one.*

### Step 2.1 — SquashStep (bound conflicts to one point, DD-2)
- **Work:** `steps/SquashStep` (`@Component`) — before `RebaseStep`, squash the PR branch to one commit (`git reset --soft <merge-base>` then commit). Wire into `contextPhase`.
- **Exit:** compile + `verify`; a clean-rebase PR (PR #2) still flows through unchanged.

### Step 2.2 — ResolveConflictsStep (agentic edit-in-place, DD-1/3/4/5)
- **Work:** `steps/ResolveConflictsStep` (`@Component`) — after `ConflictDetectionStep`. When `review.auto-resolve` is on and conflicts exist: invoke the `AgentClient` (Edit tool, rooted in the review branch) to resolve markers in place; **verify zero markers remain** (+ size/sanity guard) per file; `git add`; all-or-nothing → `git rebase --continue`. Classification stays advisory (DD-3). On any failure → escalate (DD-4), `git rebase --abort`, restore base (DD open-Q 5).
- **Config:** `review.auto-resolve` on `ReviewProperties` (DD-7), default true; runs under `maxCost` (DD-8).
- **Exit:** compile + `verify`.

### Step 2.K — Validate: auto-resolve the real conflict
- Run PR #1 (`CLAUDE.md` conflict). **Expect:** squash → rebase → resolve → `--continue` → tests run → full review, merge-readiness = clean; exit 0. Confirm the clone is restored to base on exit.
- Also re-run PR #2 (no-op resolve) + PR #3 → no regression.
- Learnings; commit.

## Stage 3 — (deferred) compile-error resolver (DD-9)
- Port `compilation_error_resolver` (Claude Edit-in-place fix of compile errors a resolution introduces). Out of scope until Stages 1–2 land.

## Then → A (generalize)
Only after Stages 1–2 are validated: fold `SquashStep`/`ResolveConflictsStep`/always-review into the **generic PR-review agent** (commonality analysis) as shared-core capabilities. *Generalize the proven, not the guessed.*
