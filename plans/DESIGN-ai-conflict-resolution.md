# DESIGN — AI Rebase / Conflict-Resolution Step

*Created 2026-07-01 · Status: **DESIGN (not yet scheduled)** · Feature owner: agentworks-pr-review · To be turned into ROADMAP steps later.*

> A workflow step that **resolves** a PR's rebase conflicts with the agent (Claude), instead of only classifying them and punting to a human. Ported from the proven Python original (`~/projects/spring-ai-project-mgmt/pr-review/`). Captured while the source mechanism is fresh; see **References** for exact `file:line`.

## 1. Problem & motivation

The Java reviewer (`agentworks-pr-review`) has **no conflict resolution**. Today:
- `RebaseStep` does `git rebase <base>`; on failure it just lists the conflicted files.
- `ConflictDetectionStep` *labels* each file SIMPLE/COMPLEX **by filename only** (`pom.xml`, `*.properties`, `package-info.java` → SIMPLE; everything else → COMPLEX) and **never resolves** — even "SIMPLE (auto-resolvable)" is a label nothing acts on.
- A COMPLEX conflict → skip tests → early report → **human review**.

Observed live (PR #1): the PR edits `CLAUDE.md`, `main`'s `CLAUDE.md` moved after submission → a genuine rebase collision → the reviewer classified it COMPLEX and stopped. A trivial **doc conflict** halted an "autonomous" reviewer.

**Value.** An autonomous PR reviewer should *resolve* stale-PR conflicts (doc/config collisions are the easy majority) and only escalate true logic conflicts. This is also a **product feature** for the Tuvium AI workshops (Vienna): "the review agent rebases your aged PR and resolves the conflicts for you." The capability existed in the Python original and was dropped in the Java port; this design restores it.

## 2. The proven design (Python original)

The load-bearing simplification: **squash the PR to one commit before rebasing**, so the rebase stops at **at most one conflict point** → a single resolution pass, **no `rebase --continue` loop**.

Flow (`pr_workflow.py`):
1. **Squash** PR → 1 commit (`intelligent_squash.py`; `reset --soft`, or a base→head patch reset). 
2. **`git rebase upstream/main`**. Clean exit → done.
3. On conflict: `conflict_analyzer.classify_complexity` labels files (`markers ≤ 2 && type ∈ {Markdown, Properties, YAML} → simple`, else complex) + renders a human-facing plan. **Advisory only — does not gate resolution.**
4. If `auto_resolve`: **per conflicted file**, invoke Claude headless (`claude -p --dangerously-skip-permissions --model sonnet`, 300 s) with a strict prompt — *"you are a file processor; output ONLY the resolved content; strip `<<<<<<< / ======= / >>>>>>>`; no prose, no fences"* + the raw conflicted body. **Claude's stdout is the resolved file.** Python strips fences/prose, **verifies zero markers remain** + a size guard (`resolvedLines > originalLines/2`) + file-type sanity, writes it back, `git add <file>`.
5. **All-or-nothing:** every file must clear → `git add .` → Java-format → **one** `git rebase --continue`. Any file unresolved / any marker left / `--continue` doesn't finish → **escalate to human** (batch mode: skip the PR).
6. Downstream (separate): `compilation_error_resolver` fixes compile errors the resolution introduced — this path uses Claude's **Edit tool in-place** (not stdout), single attempt, else human.

## 3. Java port — overview

Two new pieces + workflow rewiring:
- **`SquashStep`** — before `RebaseStep`; collapses the PR branch to one commit (`git reset --soft <merge-base>` then commit).
- **`ResolveConflictsStep`** — after `ConflictDetectionStep`, before `RunTestsStep`; on conflicts and when `review.auto-resolve` is on, invokes the `AgentClient` (Claude, Edit tool, rooted in the review branch) to resolve in place, verifies markers gone, `git add` + `git rebase --continue`; on failure → the existing early-report path.

Target pipeline (contextPhase):
```
fetch → SQUASH → rebase → detect(classify) → [conflicts? RESOLVE-CONFLICTS → verify+continue] → runTests → buildGate → …
                                                     └─ fail/escalate ─────────────► earlyReport
```

## 4. Design decisions

### DD-1 — Resolution mechanism: agentic **edit-in-place**, not stdout-scraping
**Context.** Python captured Claude's **stdout** as the resolved file and wrote it back, then stripped prose/fences with heuristics — a workaround for its headless wrapper.
**Decision.** Use our `AgentClient` (`ClaudeAgentModel` with Edit/Bash tools, `workingDirectory` = the review branch clone) and let Claude **edit the conflicted files in place**; the step then *verifies* (no `<<<<<<<`/`=======`/`>>>>>>>` markers) and `git add`s.
**Alternatives.** (a) Port the stdout-scrape verbatim — brittle prose-stripping, no repo context. (b) Ask for a unified diff/patch and apply it — extra failure mode.
**Rationale.** Our stack is already agentic and repo-rooted; in-place edit is how the compile-fix path works too. The deterministic **marker check** remains the source of truth for success, so we keep the Python design's rigor without its text-munging.

### DD-2 — **Squash-first** to bound conflicts to one point
**Context.** A multi-commit rebase can stop at several conflict points, requiring a resolve→`--continue` loop with per-step state.
**Decision.** Port `SquashStep` (squash to 1 commit before rebase) so there is **at most one** conflict stop → single-pass resolution, one `git rebase --continue`.
**Alternatives.** Implement a `resolve → git add → rebase --continue` **loop** with a max-iterations guard over multi-commit rebases.
**Rationale.** Squash-first is the proven simplification and dramatically reduces state/edge-cases. Trade-off: it rewrites the PR's commit history into one commit (acceptable for a review/merge workflow; the reviewer already works on a throwaway `review/pr-N` branch). *(Revisit if per-commit review is ever needed — then DD-2 flips to the loop.)*

### DD-3 — Classification stays **advisory**, not a gate
**Decision.** Keep `ConflictDetectionStep`'s SIMPLE/COMPLEX purely for the report + operator insight. **Attempt resolution on all conflicted files** regardless of label (matches Python: `attempt_auto_resolution` iterates *all* conflicted files).
**Rationale.** The filename/marker heuristic is too coarse to safely gate; the real safety gate is the **post-resolution verification** (DD-5) + the downstream build gate. (Optionally: surface the classification to bias escalation messaging.)

### DD-4 — **All-or-nothing** + explicit escalation policy
**Decision.** Success requires **every** conflicted file resolved + verified. Escalate to human (→ early report) when: `review.auto-resolve` is off; OR any file keeps a marker / fails validation; OR `git rebase --continue` doesn't cleanly finish.
**Rationale.** A partial resolution that "continues" produces a silently-wrong branch. All-or-nothing keeps the human-in-the-loop boundary crisp and matches the original.

### DD-5 — Verification: **markers gone** now, **build** downstream
**Decision.** The step's own success check is *zero conflict markers remain* (+ a size/sanity guard). Semantic correctness is validated **downstream** by the existing build/test gate (and, later, a compile-error-resolver — DD-9).
**Rationale.** Cheap, deterministic gate at the step; the build gate already exists to catch bad resolutions. Don't duplicate build logic inside the resolve step.

### DD-6 — Placement: after detect, inside contextPhase; failure → existing early report
**Decision.** Slot `ResolveConflictsStep` after `ConflictDetectionStep` and before `RunTestsStep`. On escalation it routes to the **existing** early-report path (no new report type).
**Rationale.** Minimal rewiring; reuses the build-gate `onFail → earlyReport` structure already proven in the v10 run.

### DD-7 — `review.auto-resolve` config flag (default **true** for the reviewer)
**Decision.** Add `review.auto-resolve` (boolean) to `ReviewProperties`. When false, conflicts skip resolution and escalate (today's behavior). Per-repo profiles set it.
**Rationale.** Some repos/teams will not want auto-resolution; the flag is the opt-out. Mirrors the Python `auto_resolve` gate.

### DD-8 — Cost bounding + model
**Decision.** Resolution runs under the existing `RunOptions.maxCost` ceiling (`review.max-cost-usd`). Model inherits the profile's `AgentClient` (Sonnet-class is enough for marker resolution).
**Rationale.** Conflict resolution is one more agent call in the same budgeted run; no separate budget needed.

### DD-9 — Compile-error resolution is a **separate, later** step
**Decision.** Defer porting `compilation_error_resolver` (Claude Edit-in-place fix of compile errors introduced by a resolution). Note it as a follow-on; the build gate already flags such breakage.
**Rationale.** Scope control — get conflict resolution landed + validated first; the compile-fix loop is an independent enhancement.

### DD-10 — Always review the diff; the build/rebase gates set *merge-readiness*, not *review-or-not*
**Context.** Today a rebase conflict (or build failure) fails the build gate → `earlyReport` → the KB-consult assess **never runs**. A trivial `CLAUDE.md` doc collision blocked the entire code review (observed live on PR #1).
**Decision.** Run the code-quality assess on the PR **diff regardless** of rebase/build outcome. The rebase/build results become a **merge-readiness** section of the report; they do **not** gate whether a code review is produced. **Every PR gets a review.**
**Alternatives.** Keep the current short-circuit — denies authors feedback whenever a PR is stale or unbuildable.
**Rationale.** A PR's code quality is independent of its mergeability; the author deserves review feedback even when the PR needs a rebase. This is **independent of** conflict-resolution (DD-1..9): resolution can still *escalate*, and we'd want the review in exactly those cases too.
**Implication (workflow restructure).** The assess should hang off `FetchPrContextStep`'s diff, not the `buildGate onPass` branch. Likely shape: `fetch → assess (always) → rebase/build → single report` merging (a) the code review and (b) a merge-readiness verdict (clean / needs-rebase / build-failing / conflict-escalated). Conflict/escalation changes the *merge-readiness* line + notes, never the presence of the review. This is its own decision — track as a distinct ROADMAP stage from the conflict-resolution step.

## 5. Open questions
1. **Squash strategy** — `reset --soft <merge-base>` vs a base→head patch reset (Python's `squash_with_patch`)? Patch reset is "no-conflict-during-squash" but needs the GitHub diff. Start with `reset --soft`.
2. **Resolution prompt** — a repo-neutral prompt, or KB-aware (consult a "how this repo resolves X" note)? Start neutral; the KB-consult discipline is for *review*, not merge.
3. **`git add` scope** — per-file after each resolve (Python) vs `git add -u` once at the end. Per-file is safer for partial-failure detection.
4. **Interaction with the customization blueprint (DD-15)** — is `ResolveConflictsStep` a shared core step (all reviewers) or overridable per repo? Likely shared core.
5. **Idempotency / cleanup** — on escalation, `git rebase --abort` and leave the clone clean (v10 left it on `review/pr-1`; the reviewer should always restore base on exit).

## 6. Evaluation / validation (fold into ROADMAP)
- **Trivial doc conflict (the live case):** PR #1 / PR #3 vs today's `main` (`CLAUDE.md` collision) → should **auto-resolve** and proceed to build/assess. This is the headline demo.
- **Simple config conflict:** a `pom.xml`/`*.properties` collision → resolve cleanly.
- **True logic conflict:** overlapping edits in the same Java method → resolution should either produce a building result *or* correctly **escalate** (never a silently-wrong merge). Verify the build gate catches a bad resolution.
- **Regression:** a clean-rebasing PR (PR #2) still flows straight through (resolve step is a no-op when there are no conflicts).
- **Cleanup:** on every exit path the clone returns to a clean base branch.

## 7. References (Python original — `~/projects/spring-ai-project-mgmt/pr-review/`)
- `pr_workflow.py`: `run_complete_workflow` :2386 (ordering); `rebase_against_upstream` :1935; `attempt_auto_resolution` :2053 (per-file, all-or-nothing); `resolve_with_claude_code` :2106 (prompt :2138, write-back :2344, marker verify :2299).
- `claude_code_wrapper.py`: `analyze_from_file` :213; command build (`claude -p --dangerously-skip-permissions --model sonnet`) :253; cwd :348.
- `conflict_analyzer.py`: `classify_complexity` :127; `analyze_conflicts` :148; `generate_plan` :312 (advisory plan/report only).
- `intelligent_squash.py`: `choose_squash_strategy` :331 (always `reset_soft`); `squash_with_patch` :241.
- `compilation_error_resolver.py`: `_fix_with_claude_code` :513; Edit-in-place prompt :457 (the DD-9 follow-on).
- `batch_pr_workflow.py`: `auto_resolve=True` :406; PR skipped on rebase failure.

## 8. Java touch-points (for the future ROADMAP)
- New: `steps/SquashStep.java`, `steps/ResolveConflictsStep.java` (both `@Component`, shared core).
- Edit: `experiment/PrReviewExperimentWorkflow` contextPhase (insert squash + resolve; wire escalation to `earlyReport`); mirror in `dsl.PrReviewDslWorkflow` if the spring-ai path wants it.
- Edit: `config/ReviewProperties` (+`autoResolve`).
- Reuse: `RebaseStep` (already creates `review/pr-N`), the `AgentClient` (agentic edit), the build gate + early-report path.
