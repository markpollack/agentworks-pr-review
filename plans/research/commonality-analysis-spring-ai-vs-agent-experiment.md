# Commonality Analysis — spring-ai reviewer ∩ agent-experiment reviewer

*Created 2026-07-01 · Input for the **generalize** phase (the generic PR-review agent, DD-15 sequel). Deferred until the full-workflow validation passes (validate-before-generalize).*

> Ground-truth structural map of the three reviewer assemblies in `agentworks-pr-review`, produced by a read-only Explore sweep. Paths relative to `src/main/`. All three workflows implement `AgentHandler<Integer, Path>` (PR number → report Path). **Headline: the two canonical reviewers share one top-level skeleton and diverge at a single swapped leaf — the assess node.**

## A. Per-assembly pipeline shape

### A1. Manual — `PrReviewWorkflow` (canonical spring-ai reference; imperative)
- `@Component` (`PrReviewWorkflow.java:70`), autowired; `config/PrReviewConfig.java:24-27` registers it into `AgentRegistry`. Orchestration hand-written in `handle()`.
- Deps (`:103-108`): `fetchPrContext`, `RebaseStep`, `conflictDetection`, `runTests`, `fixTests`, `BuildJudge`, `VersionPatternJudge`, `@Qualifier("assess-code-quality")`, `@Qualifier("assess-backport")`, `QualityJudge`, `generateReport`, `WorkshopProperties`.
- Pipeline (`:138-259`): fetch → rebase → conflict → run-tests → *(opt)* fix-tests+retest → cleanup → **T0 BuildJudge** (FAIL→early report) → **T1 VersionPatternJudge** (non-blocking) → assess-code-quality → assess-backport (sequential) → **T2 QualityJudge** → report.
- **No cost ceiling.** Journal repo **hardcoded** `spring-projects/spring-ai` (`:130`).

### A2. DSL — `PrReviewDslWorkflow` (default spring-ai path per shipped config)
- Assembled by `dsl/DslWorkflowConfig.java`, `@ConditionalOnProperty("workshop.use-dsl"="true")`.
- Top (`:46-52`): `Workflow.define("pr-review").step(contextPhase).gate(buildGate).onPass(assessAndReport).onFail(earlyReport).end()`.
- Sub-workflows: `contextPhase` = fetch→rebase→conflict→runTests→fixAndRetest→cleanup; `aiAssessment` = ExtractPrContext→**parallel(assessCodeQuality, assessBackport)**; `assessAndReport` = versionPatternStep→aiAssessment→qualityJudgeStep→assembleReport→generateReport; `earlyReport` = assembleReport→generateReport.
- **No cost ceiling.** Journal sets **no repo**.

### A3. Experiment — `PrReviewExperimentWorkflow` (agent-experiment)
- Assembled by `experiment/AgentExperimentReviewerConfig.java`, `@Profile("agent-experiment")`.
- Juries (`JuryFactory`): `buildJury` = tier0 BuildJudge / REJECT_ON_ANY_FAIL; `qualityJury` = tier0 QualityJudge / FINAL_TIER.
- Gates: `JudgeGate(buildJury, 0.5, buildMapper())`, `JudgeGate(qualityJury, 0.7, qualityMapper())` — mapper-fed JudgeGate replaces `dsl.BuildGate`/`dsl.QualityJudgeStep`; mapper bodies lifted verbatim (`:210-245`).
- Pipeline: contextPhase = fetch→rebase→conflict→runTests (**no fix/cleanup**); assessAndReport = ExtractPrContext→**assess**→qualityGate→report; top = step(contextPhase).gate(buildGate).onPass(assessAndReport).onFail(earlyReport).
- **The only one that enforces a cost ceiling** — `RunOptions.maxCost(maxCostUsd)` (`:190`); `DEFAULT_MAX_COST_USD=5.0`, overridable via `review.max-cost-usd`. Journal repo **configurable** (`.config("repo", this.repo)`).

## B. Runner selection
| Condition | Runner | Workflow |
|---|---|---|
| default, `workshop.use-dsl=true` (shipped) | `PrReviewRunner` (`@Profile("!agent-experiment")`) | **DSL** |
| `use-dsl=false`, no profile | `PrReviewRunner` | **Manual** |
| `--spring.profiles.active=agent-experiment` | `AgentExperimentReviewRunner` | **Experiment** |

Canonical spring-ai path as shipped = **DSL**; Manual is the always-on reference (`@Component`, registered under all profiles).

## D. Common workflow skeleton (the through-line)
Both canonical paths build the identical shape and diverge only inside the phases:

**`fetch-pr-context → rebase → conflict-detection → run-tests → [BUILD GATE: BuildJudge] —onFail→ assemble+generate report / —onPass→ extract-pr-context → {ASSESS} → [QUALITY GATE: QualityJudge] → assemble+generate report`**

Divergences: (1) context phase — DSL adds fixAndRetest+cleanup; (2) T1 VersionPatternStep only in DSL/manual; (3) **the assess node is the one swapped leaf** — `parallel(assessCodeQuality, assessBackport)` (spring-ai) vs single `KbConsultingAssessStep` (agent-experiment), injected as `Step<PrContext,AssessmentResult>`, writing the same `QUALITY_ASSESSMENT` key; (4) `QualityJudge.requireBackport` true vs false; (5) `dsl.BuildGate`/`QualityJudgeStep` vs mapper-fed `JudgeGate`; (6) cost ceiling only in Experiment.

## E. Generalization friction (the work list for the generic agent)

**Confirmed from the pivot javadoc:**
- **E-1** `BuildJudge`/`QualityJudge` are bare `@Component` → unconditionally registered; a profile can't override them (Exp does `new QualityJudge(client, false)`).
- **E-2** Judges injected by **concrete type**, not the existing `judge.Judge` interface → no transparent substitution.
- **E-3** `QualityJudge.requireBackport` is a **ctor flag**, not `@ConfigurationProperties`.

**Additional couplings found:**
- **E-4** Hardcoded repo strings: Manual journals `spring-projects/spring-ai`; Experiment bakes `DEFAULT_REPO`/`DEFAULT_REPO_DIR` (an absolute machine path) as compile-time constants inside the "generic" class.
- **E-5** Hardcoded gate thresholds (`0.5`/`0.7` `static final`; also baked into prompt markdown).
- **E-6** `config/PrReviewConfig` has **no `@Profile`** → the entire spring-ai graph (incl. VersionPatternJudge/AssessBackportStep) is force-instantiated under *every* profile; you can't get only the generic core.
- **E-7** Prompts are non-injectable `static final loadTemplate(...)`; a 3rd repo can't swap a prompt without a new step class. And `kb-consulting-assessment.md` is itself **agent-experiment-coupled** (control-theory text, `V(EXPLORE)` vs `J`) — not repo-neutral despite the config-driven `{briefs}`.
- **E-8** Report renderers filter on judge-name strings (`"BuildJudge"`/`"VersionPatternJudge"`), hardcode a "Backport Assessment" card, and assume the tiered spring-ai cascade. `resources/templates/report.md` is **dead** (never loaded).
- **E-9** Duplicated sources of truth: repo set twice (`github.repo` + `review.target.repo`), clone twice (`workshop.repo-dir` + `review.target.repo-dir`); nothing enforces agreement.
- **E-10** Cross-package key coupling: the agent-experiment path depends on `AssessCodeQualityStep`'s `ContextKey` constants; judge metadata keys duplicated as bare strings across judges/gates/mappers.
- **E-11** Shared deterministic steps assume Maven + a **spring-ai build flag** (`RunTestsStep` hardcodes `-Ddisable.checks=true`); `ModuleDiscovery` assumes Maven multi-module; `RebaseStep` assumes a `main` rebase model.
- **E-12** `@Primary` `AgentClient` override is **profile-wide, not step-scoped** (harmless only because the other AI steps aren't wired into the experiment pipeline).
- **E-13** `@Component` steps encode spring-ai semantics (VersionPatternJudge migration regexes, AssessBackportStep) yet load under every profile.

## Design implication (for the generic-agent DESIGN — later)
The generic core = the shared skeleton (D) with the **assess node as the injected SPI** (`Step<PrContext,AssessmentResult>`). The friction list is the extraction backlog: publish judges as `@Bean @ConditionalOnMissingBean` by `Judge` interface (E-1/E-2/E-3), profile-gate the spring-ai graph (E-6), make prompts/thresholds/repo config-injectable (E-4/E-5/E-7), decouple the report from version/backport (E-8), and de-dup the repo/clone config (E-9). The `KbConsultingAssessStep` is already the right shape (config-driven briefs); only its **prompt** needs repo-neutralizing (E-7).
