# Step 1.3 Learnings: Domain Models

## What Was Done
Created 12 Java record files in `model/` package covering all three pipeline phases:
- **Phase 1 (context)**: PrContext, FileChange, Comment, Review, Issue
- **Phase 1 (rebase/build)**: RebaseResult, Classification, ConflictFile, ConflictReport, BuildResult
- **Phase 2 (AI)**: AssessmentResult (uses `JudgmentStatus` from agent-judge-core)
- **Phase 3 (report)**: ReviewReport (uses `Judgment` from agent-judge-core)

Unit tests in `DomainModelTest.java` — 13 tests covering construction, factory methods, defensive copying, and equality.

## Key Discoveries

### Record factory method name clash
`BuildResult` has a `skipped` boolean component, so Java auto-generates an accessor `skipped()`. A static factory method `skipped()` clashes with the accessor return type (`BuildResult` vs `boolean`). Renamed to `buildSkipped()`.

**Pattern**: When naming record factory methods, avoid names that collide with component accessor names. Use a prefix (e.g., `buildSkipped()`, `resultClean()`) or different verb.

### Judgment API
- `Judgment.pass(String reasoning)` — takes only reasoning, not Score + reasoning
- `Judgment.fail(String reasoning)`, `Judgment.abstain(String reasoning)` follow same pattern
- Score is auto-set to `BooleanScore(true/false)` by these convenience methods

### Defensive copying pattern
All list-containing records use `List.copyOf()` in compact constructors. This also rejects `null` lists (throws NPE), which is the right behavior for `@NullMarked` packages.

## Deviations from Design
- `BuildResult.skipped()` renamed to `BuildResult.buildSkipped()` — design didn't anticipate the accessor clash
- No `ContextKeys.java` created yet — deferred to Step 2.0/2.1 when `ContextKey` usage becomes concrete

## Pitfalls for Next Steps
- `ReviewReport.judgments` is `List<Judgment>` (from agent-judge-core). The `Judgment` record has `Map<String, Object> metadata` which uses `Map.copyOf()` — serialization may need custom handling if metadata values aren't serializable.
- `AssessmentResult` bridges our domain to agent-judge-core's `JudgmentStatus` enum. If we later want to decouple, we'd need our own status enum.
