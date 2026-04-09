# Design: AgentWorks PR Review Pipeline

> **Created**: 2026-04-08T18:00-04:00
> **Last updated**: 2026-04-08T19:00-04:00
> **Vision version**: 2026-04-08T18:00-04:00

## Overview

A three-phase Spring Boot application that reviews pull requests against the Spring AI repository. Phase 1 (deterministic) fetches PR context from GitHub, rebases onto main, detects conflicts, and runs targeted tests. Phase 2 (AI) runs version pattern checks (deterministic gate), then code quality and backport assessments via AgentClient. Phase 3 (deterministic) assembles judge verdicts into a markdown/HTML report. Every step and judge is composed via the `workflow-flows` Workflow DSL, with AgentJournal recording the complete execution diary.

## Build Coordinates

| Field | Value |
|-------|-------|
| **Group ID** | `com.tuvium` |
| **Artifact ID** | `agentworks-pr-review` |
| **Version** | `0.1.0-SNAPSHOT` |
| **Packaging** | `jar` |
| **Java version** | 21 |
| **Spring Boot** | 4.0.x |
| **Base package** | `io.github.markpollack.prreview` |

### Module Structure

Single module (not multi-module — workshop simplicity):

```
agentworks-pr-review/
├── pom.xml
├── src/main/java/io.github.markpollack.prreview/
│   ├── PrReviewApplication.java
│   ├── PrReviewWorkflow.java
│   ├── config/
│   │   └── WorkshopConfig.java
│   ├── steps/
│   │   ├── FetchPrContext.java
│   │   ├── RebaseStep.java
│   │   ├── ConflictDetectionStep.java
│   │   ├── RunTestsStep.java
│   │   ├── AssessCodeQuality.java
│   │   ├── AssessBackport.java
│   │   └── GenerateReport.java
│   ├── judges/
│   │   ├── BuildJudge.java
│   │   ├── VersionPatternJudge.java
│   │   └── QualityJudge.java
│   ├── github/
│   │   └── GitHubRestClient.java
│   └── model/
│       ├── PrContext.java
│       ├── RebaseResult.java
│       ├── ConflictReport.java
│       ├── ConflictFile.java
│       ├── BuildResult.java
│       ├── AssessmentResult.java
│       └── ReviewReport.java
├── src/main/resources/
│   ├── application.yml
│   ├── templates/
│   │   └── report.md
│   └── prompts/
│       ├── code-quality-assessment.md
│       └── backport-assessment.md
├── src/test/java/io.github.markpollack.prreview/
│   ├── PrReviewWorkflowTest.java
│   ├── steps/
│   │   └── FetchPrContextTest.java
│   └── judges/
│       ├── BuildJudgeTest.java
│       ├── VersionPatternJudgeTest.java
│       └── QualityJudgeTest.java
├── src/test/resources/
│   └── fixtures/
│       ├── pr-5774-metadata.json
│       ├── pr-5774-files.json
│       └── pr-5774-comments.json
└── fallback/
    └── pr-5774-journal.jsonl
```

### Key Dependencies

| Dependency | GroupId | Scope | Purpose |
|------------|---------|-------|---------|
| `agentworks-bom` | `io.github.markpollack` | import | BOM for all AgentWorks versions (1.0.4) |
| `workflow-core` | `io.github.markpollack` | compile | LoopPattern, TerminationStrategy, graph composition |
| `workflow-flows` | `io.github.markpollack` | compile | Step<I,O>, Workflow DSL, AgentContext, ContextKey, JudgeGate, TieredGate |
| `journal-core` | `io.github.markpollack` | compile | Run tracking, JournalEvent hierarchy (LLMCallEvent, ToolCallEvent, GitEvent subtypes, CustomEvent). Default storage: InMemoryStorage; use JsonFileStorage for persistence. |
| `agent-judge-core` | `org.springaicommunity` | compile | Judge, Judgment, JudgmentContext, CascadedJury, Score |
| `agent-judge-llm` | `org.springaicommunity` | compile | LLM-based judge support (for QualityJudge T2) |
| `agent-client-core` | `org.springaicommunity.agents` | compile | AgentClient facade (`org.springaicommunity.agents.client` package, high-level fluent API) |
| `agent-model` | `org.springaicommunity.agents` | compile | AgentModel SPI (@FunctionalInterface) |
| `agent-claude` | `org.springaicommunity.agents` | runtime | ClaudeAgentModel — Claude Code CLI bridge via claude-code-sdk |
| `spring-boot-starter-web` | `org.springframework.boot` | compile | RestClient for GitHub API |
| `spring-boot-starter-test` | `org.springframework.boot` | test | Test framework |
| `wiremock-spring-boot` | `org.wiremock` | test | GitHub API mocking |
| `archunit-junit5` | `com.tngtech.archunit` | test | Architecture rule enforcement |

**Dependency chain for AI execution:**
```
AgentClient (agent-client-core)  ← fluent API, provider-agnostic
    → AgentModel (agent-model)   ← @FunctionalInterface SPI
    → ClaudeAgentModel (agent-claude)  ← Claude Code CLI bridge
    → claude-code-sdk 1.0.0     ← pure-Java CLI process driver
    → claude (CLI binary)        ← the actual Claude Code process
```

## Architecture

### Actual API Surface (verified from source)

#### Step<I, O> (from workflow-flows, not workflow-core)

```java
// io.github.markpollack.workflow.flows.Step
@FunctionalInterface
public interface Step<I, O> {
    O execute(AgentContext ctx, I input);

    default String name() { return getClass().getSimpleName(); }

    // Hook to publish side-channel metadata into context after execution
    default AgentContext updateContext(AgentContext ctx, O output) { return ctx; }
}
```

**Key design point**: Steps receive BOTH typed input (I→O chain) AND the shared `AgentContext`. The `updateContext()` hook lets steps publish additional data into the context for downstream consumption.

**Parameterization levels** (per docs best practices): Most steps use Level 1 (input chaining) or Level 2 (`Steps.outputOf()` for non-adjacent data). We use Level 3 (`updateContext()` with `ContextKey`) for `FetchPrContext` publishing `PR_CONTEXT` — this is the right level since multiple downstream steps and judges need `PrContext` independently. The docs advise using Level 3 sparingly.

**Built-in step types**: workflow-flows provides `AgentClientStep.of(client, template)` — but note this uses workflow-flows' own `io.github.markpollack.workflow.flows.steps.AgentClient` interface (`String execute(String prompt, AgentContext ctx)`), NOT agent-client-core's `org.springaicommunity.agents.client.AgentClient`. It's a `Step<String, String>` with simple `{input}` substitution. For our AI steps, we need to either (a) write a thin adapter bridging agent-client-core's `AgentClient` to workflow-flows' `AgentClient`, or (b) write custom Step implementations that use agent-client-core directly for richer prompt construction and structured response parsing. Evaluate in Step 3.3.

#### AgentContext (from workflow-flows — immutable accumulator)

```java
// io.github.markpollack.workflow.flows.AgentContext
public final class AgentContext {
    public <T> Optional<T> get(ContextKey<T> key);   // typesafe read
    public <T> T require(ContextKey<T> key);          // throws if absent
    public Builder mutate();                           // copy-with pattern

    // Framework keys:
    public static final ContextKey<Object> JUDGE_VERDICT;
    public static final ContextKey<String> JUDGE_REFLECTION;
    // ... plus WORKFLOW_RUN_ID, CURRENT_STEP, ACCUMULATED_COST, etc.
}
```

AgentContext is **immutable** — mutations produce new instances. The `WorkflowExecutor` auto-publishes each step's output under `Steps.outputOf(stepName)`, so any downstream step can access any prior step's output even after the I→O chain has moved on.

#### ContextKey<T> (typed context access)

```java
// io.github.markpollack.workflow.flows.ContextKey
public final class ContextKey<T> {
    public static <T> ContextKey<T> of(String key, Class<T> type);
}
```

We define project-specific context keys:
```java
public static final ContextKey<PrContext> PR_CONTEXT =
    ContextKey.of("prContext", PrContext.class);
```

#### Judge (from agent-judge-core)

```java
// org.springaicommunity.judge.Judge
@FunctionalInterface
public interface Judge {
    Judgment judge(JudgmentContext context);
}
```

#### Judgment and JudgmentStatus (the result model)

```java
// org.springaicommunity.judge.result.Judgment
public record Judgment(Score score, JudgmentStatus status, String reasoning,
                       List<Check> checks, Map<String, Object> metadata) {
    public static Judgment pass(String reasoning);
    public static Judgment fail(String reasoning);
    public static Judgment abstain(String reasoning);
    public static Judgment error(String reasoning, Throwable error);
}

// org.springaicommunity.judge.result.JudgmentStatus
public enum JudgmentStatus { PASS, FAIL, ABSTAIN, ERROR }
```

**No WARN status.** For "warning" semantics, use `PASS` with low-confidence `NumericalScore` and document findings in `Check` list. The `TieredGate` maps score ranges to PASS/ESCALATE/FAIL.

#### JudgmentContext (input to judges)

```java
// org.springaicommunity.judge.context.JudgmentContext
public record JudgmentContext(String goal, Path workspace, Duration executionTime,
                              Instant startedAt, Optional<String> agentOutput,
                              ExecutionStatus status, Optional<Throwable> error,
                              Map<String, Object> metadata) {}
```

We pass PR-specific data through the `metadata` map.

#### CascadedJury (the three-tier cascade)

```java
// org.springaicommunity.judge.jury.CascadedJury
CascadedJury.builder()
    .tier("t0-build", buildJury, TierPolicy.REJECT_ON_ANY_FAIL)
    .tier("t1-version-patterns", versionJury, TierPolicy.REJECT_ON_ANY_FAIL)
    .tier("t2-quality", qualityJury, TierPolicy.FINAL_TIER)
    .build();
```

`TierPolicy`: `REJECT_ON_ANY_FAIL` (stop cascade on FAIL), `ACCEPT_ON_ALL_PASS`, `FINAL_TIER`.

#### TieredGate (PASS/ESCALATE/FAIL — the WARN equivalent)

```java
// io.github.markpollack.workflow.flows.workflow.TieredGate
public class TieredGate<O> implements Gate<O> {
    // PASS if score >= highThreshold
    // ESCALATE if score >= lowThreshold (this is our "WARN")
    // FAIL otherwise
}
```

`GateDecision` enum: `PASS`, `FAIL`, `ESCALATE`, `TIMEOUT`.

### Components

| Component | Responsibility | API |
|-----------|---------------|-----|
| `PrReviewWorkflow` | Composes all steps and judges into Workflow DSL | `Workflow<Integer, Path>` |
| `FetchPrContext` | Gathers PR metadata, diff, comments from GitHub; publishes `PrContext` to AgentContext | `Step<Integer, PrContext>` |
| `RebaseStep` | Fetches PR branch, rebases onto main; aborts on conflict, captures conflict file list | `Step<PrContext, RebaseResult>` |
| `ConflictDetectionStep` | Classifies known conflict files as simple/complex | `Step<RebaseResult, ConflictReport>` |
| `RunTestsStep` | Discovers affected modules (reads `PrContext` from context), runs Maven tests | `Step<ConflictReport, BuildResult>` |
| `BuildJudge` | T0: deterministic — compile pass, tests pass, no complex conflicts | `Judge` → `Judgment(PASS/FAIL)` |
| `VersionPatternJudge` | T1: deterministic — Boot 3→4 pattern scan on diff (reads `PrContext` from context) | `Judge` → `Judgment(PASS/FAIL)` |
| `AssessCodeQuality` | AI code quality assessment via AgentClient (reads `PrContext` from context) | `Step<BuildResult, AssessmentResult>` |
| `AssessBackport` | AI backport candidacy assessment (reads `PrContext` from context) | `Step<AssessmentResult, AssessmentResult>` |
| `QualityJudge` | T2: LLM meta-judge on AI assessment quality | `Judge` → `Judgment(PASS/FAIL)` |
| `GenerateReport` | Reads all step outputs and judgments from AgentContext, renders report | `Step<AssessmentResult, Path>` |
| `GitHubRestClient` | Direct REST API access (no `gh` CLI) | `getPr()`, `getPrFiles()`, etc. |
| `WorkshopConfig` | Externalizes PR number, repo, timeouts | `@ConfigurationProperties` |

### Data Flow — Two Channels

Data flows through **two parallel channels**:

1. **Primary I→O chain** — each step's output becomes the next step's typed input
2. **AgentContext accumulator** — steps publish data via `updateContext()`; later steps and judges read from context via `ContextKey<T>`

```
Channel 1 (I→O):  int → PrContext → RebaseResult → ConflictReport → BuildResult → AssessmentResult → ... → Path
Channel 2 (ctx):  FetchPrContext publishes PrContext to ctx
                  RebaseStep publishes RebaseResult to ctx
                  ConflictDetectionStep publishes ConflictReport to ctx
                  RunTestsStep publishes BuildResult to ctx
                  (plus auto-published via Steps.outputOf("stepName"))
```

This solves the cross-cutting data access problem: `VersionPatternJudge` needs the diff (from `PrContext`), `RunTestsStep` needs the file list (from `PrContext`), and `GenerateReport` needs everything. All read from `AgentContext`.

**Project-specific context keys** (defined in a `ContextKeys` utility class):

```java
public final class ContextKeys {
    public static final ContextKey<PrContext> PR_CONTEXT =
        ContextKey.of("prContext", PrContext.class);
    public static final ContextKey<RebaseResult> REBASE_RESULT =
        ContextKey.of("rebaseResult", RebaseResult.class);
    public static final ContextKey<ConflictReport> CONFLICT_REPORT =
        ContextKey.of("conflictReport", ConflictReport.class);
    public static final ContextKey<BuildResult> BUILD_RESULT =
        ContextKey.of("buildResult", BuildResult.class);
    public static final ContextKey<List<AssessmentResult>> ASSESSMENTS =
        ContextKey.of("assessments", List.class);
}
```

### Rebase and Conflict Detection Flow (DD-4 detail)

```
RebaseStep:
  1. git fetch origin pull/{N}/head:{branch}
  2. git checkout {branch}
  3. git rebase main
  4. If conflict:
     a. Parse conflict file list from git rebase stderr
     b. git rebase --abort  (leave working tree clean)
     c. Return RebaseResult(success=false, conflictFiles=[...])
  5. If clean:
     a. Return RebaseResult(success=true, conflictFiles=[])

ConflictDetectionStep:
  1. Read RebaseResult.conflictFiles
  2. If empty → return ConflictReport(conflicts=[], summary="Clean rebase, no conflicts")
  3. For each file, classify based on known patterns:
     - SIMPLE: pom.xml version bumps, import reordering, whitespace
     - COMPLEX: logic changes, overlapping edits, structural refactors
  4. Return ConflictReport with per-file classification
```

No second rebase attempt. `RebaseStep` captures the information and aborts. `ConflictDetectionStep` classifies what's already known.

### Component Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                     PrReviewWorkflow                              │
│  (Workflow.define("pr-review").step(...).gate(...).build())       │
│                                                                   │
│  AgentContext flows through all steps (immutable accumulator)     │
└──┬──────────┬───────────┬──────────┬───────────┬──────────┬──────┘
   │          │           │          │           │          │
   ▼          ▼           ▼          ▼           ▼          ▼
┌──────┐ ┌────────┐ ┌──────────┐ ┌───────┐ ┌─────────┐ ┌────────┐
│Fetch │ │Rebase  │ │Conflict  │ │Run    │ │Assess   │ │Generate│
│PrCtx │ │Step    │ │Detection │ │Tests  │ │(AI x2)  │ │Report  │
└──┬───┘ └────────┘ └──────────┘ └───┬───┘ └────┬────┘ └────────┘
   │                                  │          │
   │ publishes                        │          │ reads PrContext
   │ PrContext                        │          │ from ctx
   │ to ctx                           │          │
   ▼                                  ▼          ▼
┌──────────┐              ┌──────────────────────────────┐
│GitHub    │              │  CascadedJury                 │
│RestClient│              │  ┌──────┐ ┌──────┐ ┌──────┐ │
└──────────┘              │  │Build │→│Vers. │→│Qual. │ │
                          │  │Judge │ │Judge │ │Judge │ │
                          │  │(T0)  │ │(T1)  │ │(T2)  │ │
                          │  └──────┘ └──────┘ └──────┘ │
                          │  REJECT_ON_ANY_FAIL → FINAL  │
                          └──────────────────────────────┘
```

### Workflow Composition (using actual DSL)

```java
Workflow.<Integer, Path>define("pr-review")
    // Phase 1: Deterministic context gathering
    .step(fetchPrContext)              // int → PrContext (publishes to ctx)
    .then(rebaseStep)                  // PrContext → RebaseResult
    .then(conflictDetectionStep)       // RebaseResult → ConflictReport
    .then(runTestsStep)                // ConflictReport → BuildResult (reads PrContext from ctx)
    // T0+T1 gate: deterministic judges before LLM spend
    .gate(new TieredGate<>(buildAndVersionJury, 0.8, 0.5))
        .onPass(assessCodeQuality)     // BuildResult → AssessmentResult
        .onFail(generateReport)        // skip AI, go straight to report
        .end()
    .then(assessBackport)              // AssessmentResult → AssessmentResult
    // T2 gate: LLM quality judge
    .gate(new JudgeGate<>(qualityJury, 0.7))
        .onPass(generateReport)
        .onFail(generateReport)        // still generate report, just with T2 FAIL noted
        .end()
    .build();
```

**Note**: T0 (BuildJudge) and T1 (VersionPatternJudge) are combined into a single `CascadedJury` for the first gate. T1 reads `PrContext` from `AgentContext` metadata. T2 (QualityJudge) runs as a separate gate after the AI steps.

**Per-tier verdict preservation**: `CascadedJury.vote()` collects each tier's `Verdict` into `subVerdicts`. The report can iterate `verdict.subVerdicts()` to show per-tier results independently — "T0: PASS (build clean), T1: FAIL (found javax import)". The `individualByName` map on each sub-verdict preserves judge identity. This supports the "AI said LGTM, deterministic judge said wait" teaching story.

**`onFail(generateReport)` on T0+T1 gate**: When deterministic gates fail, AI steps are skipped — no `AssessmentResult` in context. `GenerateReport` must handle absent AI assessments gracefully (use `ctx.get()` returning `Optional.empty()`, not `ctx.require()`). Same for T2 gate fail path — report generates regardless, just notes the failure.

**`PrReviewGate` is the highest-risk custom code**: It bridges `AgentContext` → `JudgmentContext.metadata()`. Test it first in Step 2.6 — unit test that constructs `AgentContext` with known `PrContext`, runs through gate, verifies judge receives diff in metadata.

---

## Data Models

### PrContext

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `number` | `int` | No | PR number |
| `title` | `String` | No | PR title |
| `description` | `String` | Yes | PR body/description |
| `author` | `String` | No | PR author login |
| `labels` | `List<String>` | No | PR labels |
| `state` | `String` | No | open/closed/merged |
| `baseBranch` | `String` | No | Target branch (e.g., main) |
| `headBranch` | `String` | No | Source branch |
| `files` | `List<FileChange>` | No | Changed files with diffs |
| `comments` | `List<Comment>` | No | PR comments |
| `reviews` | `List<Review>` | No | PR reviews |
| `linkedIssues` | `List<Issue>` | No | Linked issues |

### FileChange

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `filename` | `String` | No | File path |
| `status` | `String` | No | added/modified/removed/renamed |
| `additions` | `int` | No | Lines added |
| `deletions` | `int` | No | Lines deleted |
| `patch` | `String` | Yes | Unified diff patch |

### RebaseResult

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `success` | `boolean` | No | Whether rebase completed cleanly |
| `branch` | `String` | No | Branch name |
| `conflictFiles` | `List<String>` | No | Files with conflicts (empty if clean) |
| `errorMessage` | `String` | Yes | Error details if rebase failed |

### ConflictReport

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `conflicts` | `List<ConflictFile>` | No | Per-file conflict details |
| `hasComplexConflicts` | `boolean` | No | True if any complex conflicts exist |
| `summary` | `String` | No | Human-readable summary (never empty — "Clean rebase, no conflicts" for clean) |

### ConflictFile

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `path` | `String` | No | File path with conflict |
| `classification` | `Classification` | No | SIMPLE or COMPLEX |
| `description` | `String` | No | What kind of conflict |

### BuildResult

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `success` | `boolean` | No | Whether build/tests passed |
| `skipped` | `boolean` | No | True if skipped due to complex conflicts |
| `modules` | `List<String>` | No | Maven modules tested |
| `output` | `String` | Yes | Build output (truncated) |
| `durationMs` | `long` | No | Build duration in milliseconds |

### AssessmentResult

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `judgeName` | `String` | No | Which assessment produced this |
| `status` | `JudgmentStatus` | No | PASS/FAIL/ABSTAIN/ERROR (from agent-judge-core) |
| `score` | `double` | No | 0.0–1.0 normalized score |
| `rationale` | `String` | No | Explanation |
| `findings` | `List<String>` | No | Specific findings/issues |

### ReviewReport

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `prContext` | `PrContext` | No | Full PR context |
| `rebaseResult` | `RebaseResult` | No | Rebase outcome |
| `conflictReport` | `ConflictReport` | No | Conflict analysis |
| `buildResult` | `BuildResult` | No | Build/test outcome |
| `assessments` | `List<AssessmentResult>` | No | All judge/assessment results |
| `judgments` | `List<Judgment>` | No | Raw judge verdicts (from CascadedJury) |
| `generatedAt` | `Instant` | No | Report generation timestamp |

---

## Design Decisions

### DD-1: Single module, not multi-module

**Context**: AgentWorks itself is multi-module. Should this project follow the same pattern?

**Decision**: Single `jar` module.

**Alternatives considered**:
1. Multi-module (core, github, judges) — rejected because workshop participants need to navigate one project, not five modules with inter-dependencies

**Rationale**: Workshop teachability is the primary constraint. A single module with package-level separation (steps/, judges/, github/, model/) provides enough structure without Maven module complexity.

### DD-2: Direct REST API, no `gh` CLI

**Context**: The Python system originally used `gh` CLI for all GitHub operations.

**Decision**: Use Spring's `RestClient` for direct GitHub REST API access.

**Alternatives considered**:
1. `gh` CLI via ProcessBuilder — rejected because Broadcom SAML SSO blocks OAuth tokens for spring-projects org
2. GitHub Java SDK (org.kohsuke) — rejected because it adds a heavyweight dependency for simple REST calls

**Rationale**: RestClient is already in the Spring Boot dependency tree. Unauthenticated access works for public repos (60 req/hr). With GITHUB_TOKEN, 5000 req/hr. No SAML issues.

### DD-3: Three-tier judge cascade using CascadedJury

**Context**: The Python system used 5 separate AI assessors with no gating between them.

**Decision**: Use `CascadedJury` from agent-judge-core with three tiers:
- Tier "t0-build": `BuildJudge` with `TierPolicy.REJECT_ON_ANY_FAIL`
- Tier "t1-version-patterns": `VersionPatternJudge` with `TierPolicy.REJECT_ON_ANY_FAIL`
- Tier "t2-quality": `QualityJudge` with `TierPolicy.FINAL_TIER`

T0 and T1 are deterministic — no AI. T2 is LLM-powered. If T0 or T1 FAIL, the cascade stops and T2 never fires (no wasted LLM tokens).

**Alternatives considered**:
1. Run all judges in parallel — rejected because it wastes LLM tokens when deterministic checks fail
2. Single composite judge — rejected because it hides which tier caught the issue
3. Custom cascade implementation — rejected because `CascadedJury` already implements exactly this pattern

**Rationale**: `CascadedJury` is purpose-built for fail-fast tiered evaluation. The cascade is also the core AgentWorks teaching story for the workshop.

### DD-4: Conflict detection without auto-resolution — abort and classify

**Context**: The Python system attempted auto-resolution of conflicts, which was fragile. Need to decide behavior when rebase conflicts occur.

**Decision**: `RebaseStep` runs `git rebase main`. On conflict: capture conflict file list from stderr, `git rebase --abort` (restore clean working tree), return `RebaseResult(success=false, conflictFiles=[...])`. `ConflictDetectionStep` classifies the known file list — no second rebase needed.

**Alternatives considered**:
1. Leave working tree conflicted for inspection — rejected because `RunTestsStep` needs a buildable tree
2. Two-pass rebase (abort, then `--no-commit` to inspect) — rejected because unnecessary complexity; stderr already lists conflict files
3. Full auto-resolution — rejected because fragile, hard to teach, rarely succeeds on complex conflicts

**Rationale**: Classification is the valuable part. The pipeline acknowledges conflicts and makes intelligent decisions (skip tests on complex conflicts) without fragility.

### DD-5: Pre-recorded journal fallback for workshop reliability

**Context**: Live execution depends on GitHub API, Claude Code CLI, and network. Any can fail during a live workshop.

**Decision**: Ship a pre-recorded journal (`fallback/pr-5774-journal.jsonl`) that the pipeline can replay if live execution fails.

**Rationale**: Workshop must work even on conference WiFi. The fallback journal contains a real execution diary. The "live vs fallback" distinction is visible in the report header.

### DD-6: AgentContext as the cross-cutting data bus

**Context**: Later steps and judges need data from earlier steps that isn't in their direct I→O input. For example, `VersionPatternJudge` needs `PrContext.files` (diffs), but by that point the I→O chain is carrying `BuildResult`. `RunTestsStep` needs `PrContext.files` for module discovery, but its input is `ConflictReport`.

**Decision**: Use `AgentContext` with typed `ContextKey<T>` as the cross-cutting data bus. Each step publishes its output to context via `updateContext()`. Judges access `PrContext` through `JudgmentContext.metadata` (populated from AgentContext by the gate/jury adapter).

**Key context keys**: `PR_CONTEXT`, `REBASE_RESULT`, `CONFLICT_REPORT`, `BUILD_RESULT`, `ASSESSMENTS` (see ContextKeys utility class above).

**Rationale**: This is the intended `workflow-flows` pattern — the executor auto-publishes step outputs under `Steps.outputOf(stepName)`, and `updateContext()` supports additional custom publications. No side-channel hacks needed.

### DD-7: JudgmentStatus has no WARN — use Score + TieredGate

**Context**: The original design assumed PASS/WARN/FAIL. The actual `JudgmentStatus` enum is PASS/FAIL/ABSTAIN/ERROR — no WARN.

**Decision**: Represent "warning" findings as `Judgment.pass()` with a low `NumericalScore` (e.g., 0.6) and detailed `Check` entries. The `TieredGate` maps score ranges: score >= 0.8 → PASS, score >= 0.5 → ESCALATE (our "WARN"), score < 0.5 → FAIL. `ESCALATE` allows the workflow to continue but flags the issue in the report.

**Rationale**: Aligns with the actual API. `Check` entries provide the specific findings that the report renders. The score threshold gives a clean mapping to gate behavior.

### DD-8: Custom PrReviewGate to bridge AgentContext → JudgmentContext

**Context**: The built-in `JudgeGate.evaluate(AgentContext ctx, O output)` receives the full `AgentContext` but only passes `output.toString()` into `JudgmentContext.agentOutput`. Judges like `VersionPatternJudge` need `PrContext` (the diff), which lives in `AgentContext`, not in the gate's typed output.

**Decision**: Create `PrReviewGate` that extends or wraps `JudgeGate`, overriding `evaluate()` to enrich `JudgmentContext.metadata()` with data from `AgentContext`:

```java
public class PrReviewGate<O> implements Gate<O> {
    private final Jury jury;
    private final double highThreshold;
    private final double lowThreshold;

    @Override
    public GateDecision evaluate(AgentContext ctx, O output) {
        PrContext prCtx = ctx.require(ContextKeys.PR_CONTEXT);
        BuildResult build = ctx.get(ContextKeys.BUILD_RESULT).orElse(null);

        JudgmentContext judgmentCtx = JudgmentContext.builder()
            .goal("PR review gate")
            .agentOutput(output != null ? output.toString() : "")
            .metadata("prContext", prCtx)
            .metadata("buildResult", build)
            .metadata("diff", prCtx.files())
            .build();

        Verdict verdict = jury.vote(judgmentCtx);
        double score = extractScore(verdict);
        if (score >= highThreshold) return GateDecision.PASS;
        if (score >= lowThreshold) return GateDecision.ESCALATE;
        return GateDecision.FAIL;
    }
}
```

**Alternatives considered**:
1. Constructor-inject `PrContext` into judges — rejected because `PrContext` isn't available at jury construction time (it's produced by a step at runtime)
2. Use `Steps.outputOf("fetchPrContext")` inside judges — rejected because judges don't have access to `AgentContext`
3. Use the built-in `JudgeGate` as-is and only use `agentOutput` string — rejected because version pattern checking needs structured diff data, not a toString() dump

**Rationale**: This matches the ecosystem pattern used by `JudgmentContextFactory` in experiment-driver and `SpringAiJuryAdapter`. The gate is the natural bridge point — it has access to both `AgentContext` and the jury.

---

## Error Handling Strategy

- **Steps**: Throw on unrecoverable errors. The workflow catches, logs to Journal, and produces a partial report.
- **Judges**: Return `Judgment.fail()` or `Judgment.error()` with rationale. Never throw — a judge failure is a verdict, not an exception. `CascadedJury` has `ErrorPolicy.TREAT_AS_FAIL`.
- **GitHub API**: Retry once on 5xx. On rate limit (403), check remaining quota and fail fast with clear message.
- **AgentClient**: Timeout at 120s. On failure, the AI step returns a "skipped" AssessmentResult and the workflow continues to report generation.
- **Git operations**: Capture stderr. On rebase conflict, `--abort` and return `RebaseResult(success=false)`.

## Testing Strategy

- **Unit tests**: All judges with known `JudgmentContext` inputs → expected `Judgment` verdicts. Domain model construction/serialization. Module discovery logic.
- **WireMock**: GitHub REST API integration tests with recorded JSON fixtures.
- **Mocked AgentClient**: AI step tests with canned responses — no live Claude Code calls in tests.
- **WorkflowGraphAssert**: Use `WorkflowGraphAssert.assertTypeCompatible(graph)` on compiled workflow graphs to catch I→O type mismatches at test time, not runtime.
- **ArchUnit**: Layered architecture (steps don't import judges, judges don't import steps, github doesn't import steps). Naming conventions enforced.
- **JaCoCo**: 70% line coverage target.
- **No live integration tests in CI**: Live GitHub API and Claude Code tests exist but are `@Disabled` by default. Enabled manually for smoke testing.
- **RunOptions**: Use `RunOptions.maxCost(5.0).withMaxDuration(Duration.ofMinutes(10))` for workshop cost control and timeout safety.

## Open Questions

1. ~~What is the exact `Judge` interface?~~ **Resolved**: `Judge.judge(JudgmentContext) → Judgment`. See Interfaces section.
2. ~~Does `JudgeGate` support WARN?~~ **Resolved**: No. Use `TieredGate` with PASS/ESCALATE/FAIL. See DD-7.
3. Should the report template use Mustache or Thymeleaf? Mustache is simpler but Thymeleaf has Spring Boot auto-configuration.
4. ~~Is `agent-claude` the right dependency?~~ **Resolved**: Yes. `agent-client-core` (facade) + `agent-claude` (runtime, Claude Code CLI bridge via claude-code-sdk). See dependency chain above.
5. ~~How do we bridge `AgentContext` metadata into `JudgmentContext.metadata` for judges that need PrContext?~~ **Resolved**: The built-in `JudgeGate.evaluate()` receives `AgentContext ctx` but does NOT pass it to `JudgmentContext` — only `output.toString()` goes into `agentOutput`. Solution: create a thin custom gate class (`PrReviewGate`) that overrides `evaluate()` to read `PrContext` from `AgentContext` and populate `JudgmentContext.metadata()` before calling `jury.vote()`. This matches the ecosystem pattern (see `JudgmentContextFactory` in experiment-driver, `SpringAiJuryAdapter`). See DD-8.

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-04-08T18:00-04:00 | Initial draft | Project creation |
| 2026-04-08T19:00-04:00 | Grounded in actual APIs: Judge→Judgment, AgentContext accumulator, CascadedJury, TieredGate, DD-6 (context bus), DD-7 (no WARN) | Review feedback + source exploration |
| 2026-04-08T19:30-04:00 | Boot 4.0.x, DD-8 (PrReviewGate), resolved OQ#5 (JudgeGate gap), WorkflowGraphAssert, RunOptions, parameterization levels, AgentClientStep dual-interface clarification, journal storage correction | Docs comparison + source validation + user correction |
