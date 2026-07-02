# Step cr-1.K — Always review the diff (DD-10) — DONE

> Stage 1 of the conflict-resolution roadmap. The code review no longer depends on mergeability.

## Change
`PrReviewExperimentWorkflow`: the build gate's **both** branches now run the assess-then-report sub-workflow (extracted into an `assessAndReport(name, assess, qualityJury, generateReport)` factory, used on `onPass` and `onFail`). Retired the assess-less `earlyReport`. The build/rebase outcome is now a **merge-readiness** signal in the report, not a gate on whether we review.

- DSL reuse is safe: `generateReport` was already shared across the quality-gate branches, so reusing `assess`/`qualityJury` across the two build-gate branches works (fresh `ExtractPrContextStep`/`JudgeGate`/`reportWorkflow` per branch via the factory).

## Validation (PR #1 — the real `CLAUDE.md` conflict)
- Before: rebase conflict → build gate FAIL → `earlyReport` → **no code review**.
- After: rebase conflict → build gate FAIL → **assess runs** (consulted 2 briefs) → report contains the full KB-consult review **and** the merge-readiness (Rebase/Conflicts FAIL, Build SKIPPED). Exit 0.
- The review was correctly calibrated to the gold standard for PR #1: *"No live measurement touchpoint… only grazes Conditions… not conflating V(EXPLORE) with J… someday-add, not a flaw."*

## Open follow-up (optional Step 1.2)
The report's top-line **"Overall Verdict: FAIL"** is driven by the build gate (merge-readiness), while the review itself is PASS. The distinct sections make it readable, but a cleaner presentation would split **Code Review verdict** from **Merge Readiness verdict** in `GenerateReportStep`. Deferred — decide when touching the report for Stage 2, or as product polish.

## Next
Stage 2 (B): `SquashStep` + `ResolveConflictsStep` — make the conflict *resolvable*, not just reviewable.
