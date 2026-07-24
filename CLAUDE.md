# AgentWorks PR Review Pipeline

## Project
Java rewrite of the Python PR review/merge pipeline using the AgentWorks stack.
Workshop-teachable PR review pipeline for Spring conferences.

## Tracking
- `plans/ROADMAP.md` is the source of truth for implementation progress
- Execute steps individually, capture learnings after each step
- Read prior step learnings before starting the next step

## Build
```bash
./mvnw compile                    # compile
./mvnw test                       # unit tests (includes ArchUnit)
./mvnw verify                     # full build with quality checks (JaCoCo, spring-javaformat)
./mvnw spring-javaformat:apply    # auto-fix formatting
```

## Stack
- Spring Boot 4.0.3 (uses Jackson 3.x: `tools.jackson.databind`, NOT `com.fasterxml.jackson.databind`)
- Java 21
- Spring AI 2.0.0-M3 (transitive via workflow-flows; needs Spring milestones repo)

## v3alpha Serving Seam (branch `v3-serving` — agent-workflow ROADMAP Step 1.2)
- `serving/WorkflowV3AlphaController` serves GET `/workflow/v3alpha/view` + `/catalog`
  for the ONE pr-review workflow (no list-all — collection deferred by contract)
- `src/main/resources/v3alpha/workflow-pr-review-linear.json` is the hand-authored
  emittable linear slice (real step names, `java:pr-review.<step>:v1` refs) — it is
  **Stage 2.1's golden emitter target**; never edit it casually. Catalog instance:
  `v3alpha/operation-catalog.json` (content change ⇒ new instance id + capturedAt)
- Envelopes serialize via workflow-spec's Jackson-2 `WireJson` (Boot 4 is Jackson 3 —
  both coexist; Spring must never re-serialize v3alpha envelopes)
- JUnit: `org.junit:junit-bom:${junit-jupiter.version}` is imported FIRST in
  dependencyManagement — agentworks-bom pins jupiter 5.10.3, which breaks Boot 4's
  test machinery (compiled against JUnit 6). Keep the import first-declared.

## Key AgentWorks Dependencies (all released, no SNAPSHOTs)
- `agentworks-bom` 1.0.4 (`io.github.markpollack`)
- `workflow-flows` 0.3.0 (`io.github.markpollack`) — Step<I,O>, Workflow DSL, AgentContext, ContextKey, JudgeGate, TieredGate
- `journal-core` 0.9.0 (`io.github.markpollack`) — Run tracking, events (default: InMemoryStorage; configure JsonFileStorage for persistence)
- `agent-judge-core` 0.9.1 (`org.springaicommunity`) — Judge, Judgment, CascadedJury, Score
- `agent-client-core` 0.11.0 (`org.springaicommunity.agents`) — AgentClient facade (package: `org.springaicommunity.agents.client`)
- `agent-claude` 0.11.0 (`org.springaicommunity.agents`) — ClaudeAgentModel (runtime dep)

## AgentWorks Source
- Local source: `~/projects/agentworks/` (BOM), `~/projects/agent-workflow/` (workflow-flows), `~/projects/agent-journal/` (journal-core)
- Community source: `~/community/agent-judge/`, `~/community/agent-client/`
- Docs: `~/projects/docs/docs/agent-workflow/`, `~/community/mintlify-docs/`
- Prefer reading source over decompiling from ~/.m2

## Key API Notes (validated against source)
- Two different `AgentClient` interfaces: workflow-flows' simple one vs agent-client-core's full fluent API
- `JudgeGate` does NOT bridge AgentContext → JudgmentContext — need custom `PrReviewGate` (DD-8)
- `JudgmentStatus`: PASS/FAIL/ABSTAIN/ERROR — no WARN. Use TieredGate (ESCALATE) for warnings
- Journal git events: GitPatchEvent, GitCommitEvent, GitBranchEvent, GitPullRequestEvent

## Domain Models
- All in `io.github.markpollack.prreview.model` package, all Java records
- Record factory method names must not clash with component accessor names (e.g., `skippedBuild()` not `skipped()`)
- `Judgment.pass(String reasoning)` — convenience factory, auto-sets `BooleanScore(true)`
- `AssessmentResult` uses `JudgmentStatus` from agent-judge-core (not a local enum)
- All list-containing records use `List.copyOf()` in compact constructors (defensive + null-rejecting)

## Step Implementation Pattern
1. Implement `Step<I, O>` from `io.github.markpollack.workflow.flows`
2. Override `name()` with kebab-case name
3. Override `inputType()` and `outputType()` for `WorkflowGraphAssert`
4. Do work in `execute()`, return primary output
5. Override `updateContext()` to publish side-channel data via `ContextKey`
6. Define `ContextKey` constants as `public static final` on the producing step

## Test Infrastructure
- `TestPrContexts` / `TestAssessments` in test model package — factory methods for all domain models
- JSON fixtures in `src/test/resources/fixtures/` use raw GitHub REST API format (snake_case) — NOT domain model format
- GitHub API reference: `~/tuvium/projects/github-collector` (production DTOs, ObjectMapper with SNAKE_CASE + JavaTimeModule)
- Fallback journal deferred to Stage 4

## Steps Implementation Notes
- **RebaseStep**: ProcessBuilder-based git operations, configurable `workingDirectory(Path)`, inner `ProcessResult` record
- **ConflictDetectionStep**: Classifies by filename pattern (not conflict markers). Five compiled `Pattern`s for SIMPLE (pom.xml, build.gradle, .properties, package-info.java); everything else COMPLEX
- **RunTestsStep**: Gets PrContext from AgentContext via `ctx.require(FetchPrContextStep.PR_CONTEXT)` for module discovery. Output truncated to 10K chars (tail)
- **ModuleDiscovery**: Package-private utility in steps/. Extracts module from `/src/` marker in file path. Root files map to `.`
- ArchUnit naming rules only apply to public classes (`.arePublic()`) — package-private utilities exempt

## Judge Implementation Pattern
- `Judge` is `@FunctionalInterface`: `Judgment judge(JudgmentContext context)`
- Read structured data from `JudgmentContext.metadata()` with String key constants
- `JudgmentContext.builder().metadata(key, value)` rejects null values — guard before calling
- Use `Check.pass(name)` / `Check.fail(name, message)` for sub-assertions
- `Judgment.builder()` needs explicit score, status, reasoning, checks
- Wrap with `NamedJudge(judge, new JudgeMetadata(name, desc, JudgeType))` for metadata
- JudgeGate only passes `output.toString()` — custom gate needed for structured metadata (DD-8)

## AI Assessment Pattern
- Use agent-client-core's `AgentClient` (`org.springaicommunity.agents.client.AgentClient`)
- `agentClient.run(prompt)` → `AgentClientResponse.getResult()` → String
- Test mocks: `new AgentResponse(List.of(new AgentGeneration(text)))` → `new AgentClientResponse(response)`
- `AssessmentParser` parses JSON via regex — no Jackson dependency in parsing path
- `PromptHelper` renders file summary + diff from PrContext
- Prompt templates in `src/main/resources/prompts/` with `{placeholder}` substitution

## Architecture
Three-phase pipeline:
1. **Deterministic Context Gathering** — GitHub API, git rebase, conflict detection, tests
2. **AI Assessment** — Code quality + backport assessment via AgentClient
3. **Report Generation** — Markdown from judge verdicts

Judge cascade: **T0 (BuildJudge)** → **T1 (VersionPatternJudge)** → AI steps → **T2 (QualityJudge)**
- T0/T1 are deterministic (no AI)
- T2 only fires if T0 and T1 pass
- `skip-ai=true` gates Phase 2 entirely

## Workflow Composition
- `PrReviewWorkflow` uses manual orchestration (not Workflow DSL) for workshop readability
- Each judge call constructs `JudgmentContext` directly with `putIfNotNull()` for metadata bridging (DD-8)
- `JudgmentContext.builder().metadata(key, value)` rejects null — always guard before calling
- Type chain: FetchPrContext→PrContext→RebaseResult→ConflictReport→BuildResult; then bridges back to PrContext for AI steps

## Running
```bash
./mvnw spring-boot:run                                          # default PR
./mvnw spring-boot:run -Dspring-boot.run.arguments="5774"       # specific PR
./mvnw spring-boot:run -Dspring-boot.run.arguments="--check"    # pre-flight only
./mvnw spring-boot:run -Dspring-boot.run.arguments="--github.repo=owner/repo --workshop.default-pr=123"
```

## Python Reference
- Original Python pipeline: `~/projects/spring-ai-project-mgmt/pr-review/`
- Portable copy: `/tmp/prmerge/`

## V2 — Agent-Experiment Reviewer (KB-consulting eval-agent)

> The reviewer is generalizing beyond the spring-ai workshop tool. See `plans/VISION.md` (V2), `plans/DESIGN.md` Part 2, `plans/ROADMAP-AGENT-EXPERIMENT.md`. Stage-1 learnings: `plans/learnings/step-ae-1.K-stage1-summary.md`.

- **New package** `io.github.markpollack.prreview.experiment` — `PrReviewExperimentWorkflow` (no Spring) + `Runner` + `JuryFactory`, for `markpollack/agent-experiment`. Reuses the deterministic `steps/*` + `judges/{BuildJudge,QualityJudge}`; **drops** VersionPattern + Backport.
- **Workflow stack overridden to `0.11.0-SNAPSHOT`** (the BOM 1.12.0 pins 0.10.0) for the **mapper-fed `JudgeGate`**: `new JudgeGate<>(jury, threshold, mapper)` — the mapper populates `JudgmentContext` metadata from `AgentContext` (this **supersedes the DD-8 `PrReviewGate` workaround**). The executor records a standard `AgentContext.JUDGE_VERDICTS` trail; `AssembleReportStep` reads it.
- **`QualityJudge` generalized**: `requireBackport` flag (default true for spring-ai; the agent-experiment runner passes false; full-weight quality when backport absent).
- **KB-consulting judges** (a growing set — "more and more quickly"): each judge consults its KB(s) **by path** (read the brief, don't inline). Lenses: data-discipline (control-theory + experiment-method) and Java/Spring/DDD quality (`plans/research/java-quality-judge-knowledge-sources.md`).
- **Trace-wired + cost-capped**: the assess step uses `ClaudeAgentModel.traceDir(...)`; runs bounded by `RunOptions.maxCost`.
