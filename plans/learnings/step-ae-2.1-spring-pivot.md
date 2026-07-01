# Step ae-2.1 — KB-consult assess + the Spring pivot (DD-15)

> Step 2.1 (the KB-consulting assess) was delivered as part of the **Spring pivot** (DD-15): the agent-experiment reviewer is now a Spring `@Profile` customization — the worked example of the bean-override product blueprint. `./mvnw verify` GREEN (159 tests, no V1 regression).

## What was built
- `config/ReviewProperties` (`@ConfigurationProperties("review")`) — **the customization surface**: `target.{repo,repoDir}`, `kb.briefs[]`, `maxCostUsd`.
- `steps/KbConsultingAssessStep` (`@Component`) — reads the **configured** briefs (`review.kb.briefs`) by path; generic / profile-agnostic (any repo points config at its briefs); drop-in (publishes `AssessCodeQualityStep.QUALITY_ASSESSMENT`); shared `AssessCodeQualityStep` untouched.
- `prompts/kb-consulting-assessment.md` — STEP 1 read-briefs-by-path ("a review that didn't read the briefs is not valid"); STEP 2 the anti-over-mapping discipline (**Steps 2.2/2.3 baked in**) + JIT concept-pull; cite diff symbol + concept slug.
- `experiment/AgentExperimentReviewerConfig` (`@Profile("agent-experiment")`) — a `@Primary` trace-wired `AgentClient` + a `@Bean` reviewer (shared steps/judges + `KbConsultingAssessStep`, mapper-fed `JudgeGate`, drop VersionPattern/Backport, `RunOptions.maxCost`). + a profile-gated `CommandLineRunner`. `application-agent-experiment.yml`.
- **Retired** the no-Spring `PrReviewExperimentRunner`; **reused** `PrReviewExperimentWorkflow` (Spring-assembled) + `JuryFactory`. Added `@Profile("!agent-experiment")` to the V1 `PrReviewRunner` (no-profile behavior unchanged).

## The customization surface (the product blueprint)
A 3rd repo's reviewer = (1) `application-<repo>.yml` (`review.target.*` + `review.kb.briefs`), (2) a `@Profile("<repo>")` config (copy `AgentExperimentReviewerConfig`), (3) a profile-gated runner, (4) `--spring.profiles.active=<repo>`. **The shared core is never edited.**

## Commonality-analysis input (the generic agent — next big step)
The spring-ai V1 wiring **resists clean bean-override** in 3 places (so a customer can't yet override judges by just declaring a bean):
1. `BuildJudge`/`QualityJudge` are `@Component` → **unconditionally registered** (defeats `@ConditionalOnMissingBean`).
2. Injected by **concrete type**, not interface.
3. `QualityJudge.requireBackport` is a **constructor flag, not config** (the profile must `new QualityJudge(client, false)`).
**Generic-agent fix**: publish judges as `@Bean @ConditionalOnMissingBean` factory methods, inject by `Judge` interface + `@Qualifier`, `requireBackport` as a property → a customer overrides a judge by declaring one. *(This is the heart of the spring-ai ∩ agent-experiment commonality extraction.)*

## GTM tie-in (DD-15 + the Vaadin dossier)
The **customization (this step) IS the conversion event.** The generic agent must ship an **in-tool trial trigger** (free framework + basic review; a self-serve trial prompt + soft production nag the moment a premium judge or a repo-customization is invoked). Per `tuvium-research-conversation-agent/analysis/vaadin-open-core-gtm-funnel-2026-06.md`.

## Remaining (both claude-cost)
- **Step 2.K** — the calibration dry-run against the 3-PR gold standard (validates the agent reads the briefs + does **not** over-map; if it ignores them → add the KB-consult skill).
- **Step 3.4** — the first meaningful live run (the working-reviewer bar).
