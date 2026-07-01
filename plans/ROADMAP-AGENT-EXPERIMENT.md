# Roadmap: PR Review Agent — Agent-Experiment Track

> **Created**: 2026-06-30
> **Last updated**: 2026-06-30
> **Design version**: 2026-06-30 (`plans/DESIGN.md` Part 2 — V2 / agent-experiment generalization)
> **Program VISION**: `plans/VISION.md` (V2 section)
> **Variant**: agento-forge **eval-agent**
> **Sibling track (complete)**: `plans/ROADMAP.md` — the spring-ai workshop reviewer (Stages 1–5 done)

## Overview

The agent-experiment generalization of the PR reviewer (Forge multi-roadmap: one VISION/DESIGN, multiple `ROADMAP-*.md`). It **builds on** the completed spring-ai reviewer (`ROADMAP.md`) — project scaffolding, quality infra, and the deterministic/AI/report steps already exist — so this is **not** a fresh Stage-1 skeleton. It is the three deltas that turn the reviewer into a **KB-consulting, control-theory-grounded eval-agent** for `agent-experiment`:

- **Stage 1 — Modern reviewer** (mapper-fed `JudgeGate` foundation + the no-Spring agent-experiment assembly). *Built ad-hoc during the design conversation — recorded done; learnings consolidated in 1.K.*
- **Stage 2 — KB-consulting assessment** (the two-KB consult by instruction + the anti-over-mapping discipline).
- **Stage 3 — Evaluation harness** (the gold-standard benchmark + the calibration loss + the optimization loop) → then the first meaningful live run.

> **Commercial genesis (DESIGN DD-15).** After Step 2.1, the **spring-ai ∩ agent-experiment commonality** is extracted into a **generic, sellable PR review agent** — customized via the **Spring component model** (bean override), to **demo + sell at the Tuvium AI workshops in Vienna, summer 2026**. So: **lean into Spring component annotations as the customization blueprint**, and the agent-experiment reviewer becomes a Spring `@Profile` customization (reconsidering the no-Spring `experiment/` build).

> **Before every commit**: verify ALL exit criteria for the current step (including the standard items — see [Conventions](#conventions)). Do not delete a criterion to mark a step done — fulfill it.

## Key facts carried from the build (pre-roadmap)
- **agent-workflow** `main` `21d26eb` — mapper-fed `JudgeGate`/`TieredGate` + standard `AgentContext.JUDGE_VERDICTS`; installed `0.11.0-SNAPSHOT`.
- **agentworks-pr-review** `main` `f9696eb` — the `experiment/` reviewer + generalized `QualityJudge` + report wiring; on the SNAPSHOT.
- **Briefs**: `control-theory-kb/docs/pr-review-context-for-agent-experiment.md`, `experiment-method-kb/PR-REVIEW-AGENT-BRIEF.md`. **Gold standard**: experiment-method `findings/pr-touchpoints-agent-experiment-2026-06-29.md`.
- **Learnings prefix**: `step-ae-*` (coexist with the spring-ai track's `step-*`).

---

## Stage 1 — Modern agent-experiment reviewer (foundation)

> Stage 1 was built ad-hoc during the design conversation (it **predates** this roadmap), so its steps are recorded **done** for provenance; their per-step learnings are **consolidated retroactively in Step 1.K** rather than written as separate files.

### Step 1.0 — Design review
**Entry criteria**:
- [ ] Read: `DESIGN.md` Part 2 (DD-9…14 + Evaluation Architecture)
- [ ] Read: both KB briefs; `~/tuvium/projects/agento-university/plans/inbox/agent-workflow-modernization-2026-06-29.md`

**Work items**:
- [x] CONFIRM the V2 design against the already-built reviewer; record deltas / open questions (resolved in DESIGN Part 2 — Open Questions V2)

**Exit criteria**:
- [x] Design reviewed (this roadmap + DESIGN Part 2 are the record); learnings → consolidated in Step 1.K; COMMIT

**Deliverables**: the V2 design, validated against the built reviewer.

### Step 1.1 — JudgeGate root fix (agent-workflow) ✅ DONE
**Entry criteria**:
- [x] Step 1.0 complete

**Work items**:
- [x] `JudgeGate`/`TieredGate` **require** an `AgentContext→JudgmentContext` mapper (DD-10); remove the no-mapper constructors; `defaultContextMapper` opt-in
- [x] `WorkflowExecutor` records every gate verdict (PASS+FAIL) to standard `AgentContext.JUDGE_VERDICTS`
- [x] Non-mocked `GateTest` (real `SimpleJury` reading metadata; PASS-path trail)

**Exit criteria**:
- [x] `workflow-flows` suite green; merged agent-workflow `main` `21d26eb`; installed `0.11.0-SNAPSHOT`; work order in agent-workflow inbox; learnings → Step 1.K

**Deliverables**: the framework fix the reviewer rests on.

### Step 1.2 — No-Spring agent-experiment assembly ✅ DONE
**Entry criteria**:
- [x] Step 1.1 complete

**Work items**:
- [x] `experiment/PrReviewExperimentWorkflow` + `Runner` + `JuryFactory` (DD-9, DD-14)
- [x] Reuse `steps/*` + `judges/{BuildJudge,QualityJudge}` via mapper-fed `JudgeGate`; **drop** `VersionPatternJudge` + `AssessBackportStep`; lift the `spring-ai` literal to config
- [x] Trace-wired assess (`ClaudeAgentModel.traceDir`) + `RunOptions.maxCost`

**Exit criteria**:
- [x] `./mvnw compile` green; learnings → Step 1.K

**Deliverables**: the no-Spring agent-experiment reviewer assembly.

### Step 1.3 — QualityJudge generalization + report wiring ✅ DONE
**Entry criteria**:
- [x] Step 1.2 complete

**Work items**:
- [x] `QualityJudge` `requireBackport` flag (DD-13; default true for spring-ai, false for agent-experiment)
- [x] `AssembleReportStep` reads the standard `JUDGE_VERDICTS` + the assess output (backward-compatible)

**Exit criteria**:
- [x] `./mvnw verify` green (no regression to the spring-ai reviewer); committed agentworks-pr-review `main` `f9696eb`; learnings → Step 1.K

**Deliverables**: a functional, verified reviewer.

### Step 1.K — Stage 1 consolidation ✅ DONE
**Entry criteria**:
- [x] All Stage 1 steps complete (1.0–1.3)
- [x] Read: this roadmap's Stage 1 record + the build commits (`21d26eb`, `f9696eb`)

**Work items**:
- [x] COMPACT Stage-1 learnings → `plans/learnings/LEARNINGS.md` Part 2: the **DD-8→DD-10 through-line**, the **`QualityJudge` backport-coupling** finding, the **`0.11.0-SNAPSHOT` dependency** state, the **KB-consulting-judges** principle
- [x] UPDATE `CLAUDE.md` with the Stage-1 distilled learnings (V2 section)
- [x] Phase Review folded into `step-ae-1.K-stage1-summary.md` — **no MUST-FIX** (verify green, no V1 regression)

**Exit criteria**:
- [x] `LEARNINGS.md` updated; Created `plans/learnings/step-ae-1.K-stage1-summary.md`; `CLAUDE.md` updated; ROADMAP checkboxes updated; COMMIT

**Deliverables**: consolidated Stage-1 narrative; the reviewer's provenance captured.

---

## Stage 2 — KB-consulting assessment

### Step 2.0 — Stage 2 entry *(inter-stage gate — do not skip)*
**Entry criteria**:
- [ ] Stage 1 consolidation complete — Read: `plans/learnings/step-ae-1.K-stage1-summary.md`
- [ ] Read: `plans/learnings/LEARNINGS.md`; both KB briefs; `DESIGN.md` Part 2 DD-11

**Work items**:
- [ ] REVIEW the Stage 1 summary for deferred decisions affecting Stage 2
- [ ] VERIFY the two KB briefs are current (the KB agents own them; confirm paths/section anchors)

**Exit criteria**:
- [ ] Context loaded; no blocking issues; Create: `plans/learnings/step-ae-2.0-stage2-entry.md`; ROADMAP checkboxes updated; COMMIT

**Deliverables**: verified entry into Stage 2.

### Step 2.1 — KB-aware assess step (consult by instruction)
**Entry criteria**:
- [ ] Step 2.0 complete; Read: `plans/learnings/step-ae-2.0-stage2-entry.md` — prior step learnings

**Work items** — delivered as part of the **Spring pivot (DD-15)**:
- [x] BUILT `steps/KbConsultingAssessStep` (`@Component`, config-driven briefs via `ReviewProperties.kb.briefs`) + `prompts/kb-consulting-assessment.md` — instructs the agent to **read the configured briefs by path** before assessing (KB not inlined); shared `AssessCodeQualityStep` untouched; drop-in (publishes `QUALITY_ASSESSMENT`). Assembled by `@Profile("agent-experiment")` `AgentExperimentReviewerConfig` (the bean-override blueprint) + a profile-gated `CommandLineRunner`; `application-agent-experiment.yml` points `review.kb.briefs` at the two briefs.
- [x] **Steps 2.2 (discipline) + 2.3 (JIT pull) are baked into the same prompt** — the anti-over-mapping rules + the JIT concept-pull instruction.
- [ ] **Dry-run verification = Step 2.K** (the calibration check against the 3-PR gold standard — claude-cost). If the agent ignores the consult there, add the **KB-consult skill** fallback.

**Exit criteria**:
- [x] The Spring-pivoted KB-consulting reviewer is built; `./mvnw verify` GREEN (159 tests, no V1 regression); committed. Consult/discipline/JIT live in the prompt; validation is Step 2.K. → `plans/learnings/step-ae-2.1-spring-pivot.md`.

**Deliverables**: the consult-instructing assess prompt (+ skill fallback if needed).

### Step 2.2 — Discipline guardrails
**Entry criteria**:
- [ ] Step 2.1 complete; Read: `plans/learnings/step-ae-2.1-consult-prompt.md` — prior step learnings

**Work items**:
- [ ] BAKE the anti-over-mapping rules into the prompt (most PRs = no touchpoint; optional add ≠ flaw; ground both sides; live vs latent; never conflate `V(EXPLORE)` with `J`)
- [ ] ADD the control-theory **8 red-flags** + the output shape (experiment-method §5)

**Exit criteria**:
- [ ] Guardrails present (asserted in the prompt test); `./mvnw test` green; Create: `plans/learnings/step-ae-2.2-guardrails.md`; `CLAUDE.md` updated; ROADMAP checkboxes updated; COMMIT

**Deliverables**: the disciplined assess prompt.

### Step 2.3 — JIT KB-pull (same consult mechanism as 2.1)
**Entry criteria**:
- [ ] Step 2.2 complete; Read: `plans/learnings/step-ae-2.2-guardrails.md` — prior step learnings

**Work items**:
- [ ] EXTEND the consult instruction (the *same* read-by-path mechanism, not a separate design): on a trigger (journaling / cost / trace / jury / `ExperimentResult`·`ItemResult`·`Sweep`·`InvocationResult` / result store / workspace materialization / `SourceRef` / new persisted field), the agent reads the relevant `concepts/<slug>.md` / `findings/*.md`; the journaling contract (`agent-journal/.../journal-capture-DESIGN.md`) is the law

**Exit criteria**:
- [ ] A triggering PR pulls the right concept; a non-triggering PR pulls nothing; `./mvnw test` green; Create: `plans/learnings/step-ae-2.3-jit-pull.md`; `CLAUDE.md` updated; ROADMAP checkboxes updated; COMMIT

**Deliverables**: the JIT concept-pull on triggers.

### Step 2.K — Stage 2 consolidation + first calibration check
**Entry criteria**:
- [ ] All Stage 2 steps complete (2.0–2.3); Read: all `plans/learnings/step-ae-2.*`

**Work items**:
- [ ] DRY-RUN the assess prompt against the **3-PR gold standard** (recorded diffs — no live claude needed). **Hard gate: it must not over-map** (PR#3 → Manifest, PR#1 → grazes Conditions, PR#2 → no touchpoint). Over-mapping fails the stage and routes back to the prompt / skill.
- [ ] COMPACT Stage-2 learnings → `LEARNINGS.md`; UPDATE `CLAUDE.md`; run the Phase Review

**Exit criteria**:
- [ ] The assess prompt reproduces the calibration on the 3 PRs (no over-mapping); `LEARNINGS.md` updated; Create: `plans/learnings/step-ae-2.K-stage2-summary.md`; `CLAUDE.md` updated; ROADMAP checkboxes updated; COMMIT

**Deliverables**: a calibrated, non-over-mapping assess prompt.

---

## Stage 3 — Evaluation harness + optimization (eval-agent)

### Step 3.0 — Stage 3 entry *(inter-stage gate — do not skip)*
**Entry criteria**:
- [ ] Stage 2 consolidation complete — Read: `plans/learnings/step-ae-2.K-stage2-summary.md`
- [ ] Read: `plans/learnings/LEARNINGS.md`; `DESIGN.md` Part 2 — Evaluation Architecture

**Work items**:
- [ ] REVIEW the Stage 2 summary; VERIFY the eval-architecture design assumptions still hold (judges, loss, benchmark)

**Exit criteria**:
- [ ] Context loaded; Create: `plans/learnings/step-ae-3.0-stage3-entry.md`; ROADMAP checkboxes updated; COMMIT

**Deliverables**: verified entry into Stage 3.

### Step 3.1 — Benchmark case models *(eval-agent infra)*
**Entry criteria**:
- [ ] Step 3.0 complete; Read: `plans/learnings/step-ae-3.0-stage3-entry.md`; `DESIGN.md` Part 2 — Benchmark Cases

**Work items**:
- [ ] MODEL benchmark cases `{ repo, prNumber, expectedVerdict, expectedTouchpoints[] }` from experiment-method `findings/pr-touchpoints-…`
- [ ] LOAD the 3 gold-standard PRs; query methods (next-unevaluated, all-cases)
- [ ] WRITE unit tests for the case model + query logic

**Exit criteria**:
- [ ] Cases load + tests pass; Create: `plans/learnings/step-ae-3.1-benchmark-cases.md`; `CLAUDE.md` updated; ROADMAP checkboxes updated; COMMIT

**Deliverables**: the benchmark case model + the calibrated 3-PR set.

### Step 3.2 — Meta-judges *(eval-agent infra)*
**Entry criteria**:
- [ ] Step 3.1 complete; Read: `plans/learnings/step-ae-3.1-benchmark-cases.md`; `DESIGN.md` Part 2 — Judges

**Work items**:
- [ ] IMPLEMENT `CalibrationJudge` (deterministic; **hard over-map penalty**), `GroundednessJudge` (AI; diff+slug), `RedFlagJudge` (AI; control-theory §4)
- [ ] UNIT-test each (known agent-review → known score); AI judges with a mock model

**Exit criteria**:
- [ ] Judges compile + tests pass; Create: `plans/learnings/step-ae-3.2-meta-judges.md`; `CLAUDE.md` updated; ROADMAP checkboxes updated; COMMIT

**Deliverables**: the three review-evaluating judges.

### Step 3.3 — Loss + optimization loop
**Entry criteria**:
- [ ] Step 3.2 complete; Read: `plans/learnings/step-ae-3.2-meta-judges.md`; `DESIGN.md` Part 2 — Loss Function

**Work items**:
- [ ] COMPOSITE loss = 1 − weighted_sum(scores)/max_score; **primary metric = over-map rate (target 0)**
- [ ] WIRE the loop: run the reviewer on the benchmark → judge → loss → tune the prompt/KB-consult → repeat until loss converges; journaled

**Exit criteria**:
- [ ] Loss computed on the benchmark; ≥1 optimization iteration recorded (prompt delta + loss trajectory); `./mvnw test` green; Create: `plans/learnings/step-ae-3.3-optimization-loop.md`; `CLAUDE.md` updated; ROADMAP checkboxes updated; COMMIT

**Deliverables**: the loss + optimization loop.

### Step 3.4 — First meaningful live run
**Entry criteria**:
- [ ] Step 3.3 complete; Read: `plans/learnings/step-ae-3.3-optimization-loop.md`

**Work items**:
- [ ] RUN the reviewer on one of Paul's real PRs via `~/scripts/claude-run.sh` (escapes the session process tree): rebase + agent-experiment tests + the **KB-informed** assessment
- [ ] PRODUCE `reports/review-pr-N.{md,html,json}` + the journal trace — *this is the ROADMAP-STEWARD Step 2.2b exit-criteria artifact*

**Exit criteria**:
- [ ] A real, readable, KB-informed review artifact exists, journaled; it does **not** over-map; Create: `plans/learnings/step-ae-3.4-first-live-run.md`; `CLAUDE.md` updated; ROADMAP checkboxes updated; COMMIT

**Deliverables**: the first real agent-experiment review (the steward's `[View review]` artifact).

### Step 3.K — Stage 3 consolidation + capability boundary
**Entry criteria**:
- [ ] All Stage 3 steps complete (3.0–3.4); Read: all `plans/learnings/step-ae-3.*`

**Work items**:
- [ ] COMPACT all learnings → `LEARNINGS.md`; document the reviewer's calibration boundary (where it over/under-maps) + cost characteristics
- [ ] DECIDE the `ReviewProfile` SPI timing (OQ-4, held) — extract now (reconciled with `bud-review-core`) or after a third reviewer
- [ ] Run the Phase Review

**Exit criteria**:
- [ ] `LEARNINGS.md` covers all stages; Create: `plans/learnings/step-ae-3.K-stage3-summary.md`; `CLAUDE.md` updated; ROADMAP checkboxes updated; COMMIT

**Deliverables**: the eval'd reviewer + a decision on the template.

---

## Conventions

### Commit convention
Every step ends with a git commit: `Step ae-X.Y: brief description`.

### Step entry criteria convention
Every step's entry criteria includes (beyond step-specific reads):
- [ ] Previous step complete
- [ ] Read: `plans/learnings/step-ae-{PREV}-{topic}.md` — prior step learnings

### Step exit criteria convention
Every step's exit criteria includes (beyond step-specific criteria):
- [ ] All tests pass: `./mvnw test` (or `verify`) — *where the step produced code*
- [ ] Create: `plans/learnings/step-ae-X.Y-topic.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update this ROADMAP's checkboxes
- [ ] COMMIT

### Consolidation convention
The `.K` step of each stage compacts all per-step learnings into `LEARNINGS.md`, updates `CLAUDE.md`, creates a `step-ae-X.K-stageN-summary.md`, and runs the Forge Phase Review.

### Inter-stage gate convention
The first step of Stage N (N > 1) gates on Stage N-1's consolidation — reading `step-ae-(N-1).K-stageN-1-summary.md` + `LEARNINGS.md` — before any Stage-N work.

## Cross-references
- **Steward consumption**: `agento-university/plans/ROADMAP-STEWARD.md` Step 2.2b / 2.4 — the steward *runs* this reviewer and lights the campus billboard `[View review]`. This roadmap **owns the reviewer**; ROADMAP-STEWARD owns the steward that invokes it. The Step 3.4 artifact satisfies ROADMAP-STEWARD's Step 2.2b exit criterion.
- **Template (later)**: the `ReviewProfile` SPI / `pr-review-agent-template` (ROADMAP-STEWARD Iteration 2.5), reconciled with the earlier `bud-review-core` extraction.

## Revision History
| Timestamp | Change | Trigger |
|---|---|---|
| 2026-06-30 | Initial — agent-experiment generalization track (Forge multi-roadmap, eval-agent variant); Stage 1 recorded done, Stages 2–3 planned | DESIGN Part 2 (V2) + steward-first direction |
| 2026-06-30 | Open-questions resolved (consult-by-path + skill fallback; hard over-map gate; JIT folded into consult; SPI held); form review — added per-step Entry/Exit/Deliverables + the standard convention items on every step | User review |
