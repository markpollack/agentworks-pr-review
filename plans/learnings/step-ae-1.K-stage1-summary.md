# Step ae-1.K — Stage 1 Summary (Agent-Experiment / V2)

> Consolidation of V2 Stage 1 (the modern agent-experiment reviewer). The work was built **ad-hoc during the design conversation** (it predates `ROADMAP-AGENT-EXPERIMENT.md`); this is its retroactive consolidation — so there are no separate `step-ae-1.{1,2,3}` files, just this summary.

## What Stage 1 produced
- **agent-workflow** (`main` `21d26eb`, installed `0.11.0-SNAPSHOT`): `JudgeGate`/`TieredGate` now **require** an `AgentContext→JudgmentContext` mapper; `WorkflowExecutor` records a standard `AgentContext.JUDGE_VERDICTS` trail (PASS **and** FAIL); non-mocked `GateTest`.
- **agentworks-pr-review** (`main` `f9696eb`): a no-Spring `experiment/` reviewer (`PrReviewExperimentWorkflow` + `Runner` + `JuryFactory`) for `markpollack/agent-experiment` — reuses the deterministic `steps/*` + `judges/{BuildJudge,QualityJudge}` via the mapper-fed `JudgeGate`; **drops** the spring-ai VersionPattern + Backport tiers; trace-wired assess + `RunOptions.maxCost`. `QualityJudge` generalized (`requireBackport` flag); `AssembleReportStep` reads the standard `JUDGE_VERDICTS` + the assess output. `./mvnw verify` green — no regression to the spring-ai reviewer.

## Key learnings (the through-lines)
1. **DD-8 → DD-10: the JudgeGate gap got fixed at the root.** V1 identified that `JudgeGate` passes only `output.toString()` and worked around it with a custom `PrReviewGate`/`BuildGate` (DD-8; LEARNINGS #5/#20/#27, pitfall #2). V2 fixed it in the framework — the mapper is a required gate input, and it *is* the old bridge body. **DD-8's workaround is obsolete**; the per-consumer gate is gone. Several V1 learnings about the gap are now historical.
2. **`QualityJudge` was more spring-ai-coupled than any analysis found** — it hardwired a "backport must be present → FAIL" check, so dropping Backport FAILed the quality tier *by construction*. Generalized via a `requireBackport` flag (default true for spring-ai + its tests; false for agent-experiment). **A by-hand generalization surfaces couplings reading can't.**
3. **The report depends on the verdict trail, not the gates.** The old hand-rolled gates wrote `DslContextKeys.JUDGMENTS`; the framework `JudgeGate` doesn't. Fixed at the root too — the executor records `JUDGE_VERDICTS`; `AssembleReportStep` merges it + the assess output, backward-compatibly.
4. **The reviewer accrues KB-consulting judges** (Mark: *"more and more of those quickly"*). Each judge consults its KB(s) **by path** (read the brief, don't inline). Two lenses identified: the **data-discipline** judge (control-theory + experiment-method) and a **Java/Spring/DDD quality** judge (`plans/research/java-quality-judge-knowledge-sources.md`). Stage 2's consult mechanism must stay **judge-agnostic** so adding a judge = adding a brief reference.

## Build state carried forward (facts, not debt)
- pr-review overrides `workflow-flows` to **`0.11.0-SNAPSHOT`** (the BOM 1.12.0 pins 0.10.0) for the mapper-fed JudgeGate. Drop the override once 0.11.0 is released into agentworks-bom (release deferred — more framework changes expected).
- The reviewer is built + verified but **not yet KB-informed (Stage 2) or run live (Stage 3.4)** — those are the "completing PR review" bar.

## Phase Review (MUST / SHOULD / CONSIDER)
- **MUST-FIX**: none — compiles + `verify` green; no regression to the spring-ai reviewer.
- **SHOULD**: the reviewer isn't *useful* until Stage 2 (KB-consult) + the first live review — those are the next steps, not gaps.
- **CONSIDER**: keep the Stage-2 consult mechanism judge-agnostic (Java-quality judge slots in next by reference); bake `licenseTier` into `status.json` (governs steward actions); the `ReviewProfile` SPI extraction is held (OQ-4) until ≥2 reviewers.
