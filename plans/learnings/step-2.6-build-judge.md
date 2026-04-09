# Step 2.6: BuildJudge (T0) — Learnings

**Completed**: 2026-04-08

## What Was Built

`BuildJudge` implementing `Judge` (functional interface from agent-judge-core) — T0 deterministic judge that evaluates build health with no AI. Four `Check` sub-assertions: rebase-clean, no-complex-conflicts, build-executed, tests-passed.

## Key Decisions

1. **Judge, not JudgeWithMetadata** — Implemented the raw `Judge` functional interface. Can be wrapped with `NamedJudge` later for metadata (name, description, JudgeType.DETERMINISTIC) when wiring the cascade.

2. **Metadata keys as String constants** — `REBASE_RESULT`, `CONFLICT_REPORT`, `BUILD_RESULT` defined on BuildJudge. The custom workflow gate (DD-8) will populate these in `JudgmentContext.metadata()` from `AgentContext`.

3. **BooleanScore, not NumericalScore** — Build health is pass/fail, not a gradient. `BooleanScore(true)` for all-pass, `BooleanScore(false)` for any failure.

4. **No WARN state** — `JudgmentStatus` has PASS/FAIL/ABSTAIN/ERROR but no WARN. Simple conflicts (without complex ones) are PASS — they don't degrade the build. The TieredGate ESCALATE path handles warning semantics at the workflow level.

5. **tests-passed check omitted when build skipped** — If `BuildResult.skipped()` is true, there's no test result to evaluate. Only 3 checks in that case.

## Judge API Learnings

- `Judge` is `@FunctionalInterface` — single method `Judgment judge(JudgmentContext context)`
- `JudgmentContext.builder().metadata(key, value)` does NOT accept null values — guard with null check before calling
- `Check.pass(name)` and `Check.fail(name, message)` — factory methods on the Check record
- `Judgment.builder()` requires explicit `.score()`, `.status()`, `.reasoning()`, `.checks()` — no defaults
- `NamedJudge` wraps any Judge with `JudgeMetadata(name, description, JudgeType)` — composition over inheritance

## JudgeGate Confirmation (DD-8)

Confirmed from source: `JudgeGate.evaluate()` constructs `JudgmentContext` with only `output.toString()` as `agentOutput`. No structured data from `AgentContext` is bridged. Custom gate needed for Steps 4.2 to populate metadata keys.

## Test Coverage (10 tests)

- Full green build (4/4 checks pass)
- Rebase failure (conflict in files)
- Missing rebase result (null)
- Complex conflicts (FAIL)
- Simple-only conflicts (PASS)
- Build skipped (FAIL)
- Tests failed (FAIL)
- Missing build result (null)
- Check counting (4 checks when executed, no tests-passed when skipped)
