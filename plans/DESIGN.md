# Design: AgentWorks PR Review Pipeline

> **Created**: 2026-04-08T18:00-04:00
> **Last updated**: 2026-04-08T18:00-04:00
> **Vision version**: 2026-04-08T18:00-04:00

## Overview

A three-phase Spring Boot application that reviews pull requests against the Spring AI repository. Phase 1 (deterministic) fetches PR context from GitHub, rebases onto main, detects conflicts, and runs targeted tests. Phase 2 (AI) runs version pattern checks (deterministic gate), then code quality and backport assessments via AgentClient. Phase 3 (deterministic) assembles judge verdicts into a markdown/HTML report. Every step and judge is composed via the AgentWorkflow DSL, with AgentJournal recording the complete execution diary.

## Build Coordinates

| Field | Value |
|-------|-------|
| **Group ID** | `com.tuvium` |
| **Artifact ID** | `agentworks-pr-review` |
| **Version** | `0.1.0-SNAPSHOT` |
| **Packaging** | `jar` |
| **Java version** | 21 |
| **Base package** | `com.tuvium.prreview` |

### Module Structure

Single module (not multi-module — workshop simplicity):

```
agentworks-pr-review/
├── pom.xml
├── src/main/java/com/tuvium/prreview/
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
├── src/test/java/com/tuvium/prreview/
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
| `workflow-core` | `io.github.markpollack` | compile | Step<I,O>, Workflow DSL, AgentContext, JudgeGate |
| `journal-core` | `io.github.markpollack` | compile | Run tracking, JournalEvent hierarchy, JsonFileStorage |
| `agent-judge-core` | `org.springaicommunity` | compile | Judge interface, verdict model |
| `agent-judge-llm` | `org.springaicommunity` | compile | LLM-based judge support (for QualityJudge T2) |
| `agent-client-core` | `org.springaicommunity.agents` | compile | AgentClient abstraction |
| `agent-claude` | `org.springaicommunity.agents` | compile | Claude Code AgentClient implementation |
| `spring-boot-starter-web` | `org.springframework.boot` | compile | RestClient for GitHub API |
| `spring-boot-starter-test` | `org.springframework.boot` | test | Test framework |
| `wiremock-spring-boot` | `org.wiremock` | test | GitHub API mocking |
| `archunit-junit5` | `com.tngtech.archunit` | test | Architecture rule enforcement |

## Architecture

### Components

| Component | Responsibility | Public API |
|-----------|---------------|------------|
| `PrReviewWorkflow` | Composes all steps and judges into workflow DSL | `run(int prNumber)` |
| `FetchPrContext` | Gathers PR metadata, diff, comments from GitHub | `Step<Integer, PrContext>` |
| `RebaseStep` | Fetches PR branch, rebases onto main | `Step<PrContext, RebaseResult>` |
| `ConflictDetectionStep` | Classifies conflicts as simple/complex | `Step<RebaseResult, ConflictReport>` |
| `RunTestsStep` | Discovers affected modules, runs Maven tests | `Step<ConflictReport, BuildResult>` |
| `BuildJudge` | T0: deterministic build/test/conflict verdict | `Judge` → PASS/WARN/FAIL |
| `VersionPatternJudge` | T1: deterministic Boot 3→4 pattern scan | `Judge` → PASS/WARN/FAIL |
| `AssessCodeQuality` | AI code quality assessment via AgentClient | `Step<PrContext, AssessmentResult>` |
| `AssessBackport` | AI backport candidacy assessment | `Step<PrContext, AssessmentResult>` |
| `QualityJudge` | T2: LLM meta-judge on AI assessment quality | `Judge` → PASS/WARN/FAIL |
| `GenerateReport` | Renders markdown/HTML from judge verdicts | `Step<ReviewReport, Path>` |
| `GitHubRestClient` | Direct REST API access (no `gh` CLI) | `getPr()`, `getPrFiles()`, etc. |
| `WorkshopConfig` | Externalizes PR number, repo, timeouts | `@ConfigurationProperties` |

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     PrReviewWorkflow                         │
│  (Workflow DSL — composes steps + judge gates)               │
└──┬──────────┬──────────┬──────────┬──────────┬──────────┬───┘
   │          │          │          │          │          │
   ▼          ▼          ▼          ▼          ▼          ▼
┌──────┐ ┌────────┐ ┌─────────┐ ┌───────┐ ┌───────┐ ┌────────┐
│Fetch │ │Rebase  │ │Conflict │ │Run    │ │Assess │ │Generate│
│PrCtx │ │Step    │ │Detect   │ │Tests  │ │(AI)   │ │Report  │
└──┬───┘ └────────┘ └─────────┘ └───┬───┘ └───┬───┘ └────────┘
   │                                 │         │
   ▼                                 ▼         ▼
┌──────────┐              ┌──────┐ ┌──────┐ ┌──────┐
│GitHub    │              │Build │ │Vers. │ │Qual. │
│RestClient│              │Judge │ │Judge │ │Judge │
└──────────┘              │(T0)  │ │(T1)  │ │(T2)  │
                          └──────┘ └──────┘ └──────┘
                              │        │        │
                              └────────┴────────┘
                              Judge Cascade Gate
                              T0 → T1 → T2
```

### Data Flow

```
PR Number (int)
    │
    ▼
FetchPrContext ──→ PrContext (metadata, diff, comments, issues)
    │
    ▼
RebaseStep ──→ RebaseResult (success/conflict, branch)
    │
    ▼
ConflictDetectionStep ──→ ConflictReport (per-file classification, summary)
    │
    ▼
RunTestsStep ──→ BuildResult (pass/fail, modules, duration, output)
    │
    ▼
BuildJudge [T0 GATE] ──→ PASS/WARN/FAIL
    │                       │
    │ (FAIL → skip AI,      │ (PASS/WARN → continue)
    │  go to report)        │
    ▼                       ▼
                    VersionPatternJudge [T1 GATE] ──→ PASS/WARN/FAIL
                            │                           │
                            │ (FAIL → skip AI,          │ (PASS/WARN)
                            │  go to report)            │
                            ▼                           ▼
                                                AssessCodeQuality ──→ AssessmentResult
                                                AssessBackport ──→ AssessmentResult
                                                        │
                                                        ▼
                                                QualityJudge [T2] ──→ PASS/WARN/FAIL
                                                        │
                                                        ▼
                                                GenerateReport ──→ reports/review-pr-{N}.{md,html}
```

## Interfaces

### Step<I, O> (from workflow-core)

```java
public interface Step<I, O> {
    O execute(I input, AgentContext context);
}
```

**Contract**:
- Each step receives typed input and produces typed output
- AgentContext provides Journal access for event logging
- Steps are stateless — all state flows through input/output types

### Judge (from agent-judge-core)

```java
public interface Judge {
    Verdict evaluate(Object evidence);
}
```

**Contract**:
- Returns a Verdict with status (PASS/WARN/FAIL), confidence, and rationale
- Deterministic judges (T0, T1) must be side-effect-free and fast
- LLM judges (T2) may call AgentClient and log LLMCallEvent

**Error handling**: Steps throw on unrecoverable errors; the workflow catches and logs to Journal. Judge failures are FAIL verdicts, not exceptions.

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
| `errorMessage` | `String` | Yes | Error details if rebase failed |

### ConflictReport

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `conflicts` | `List<ConflictFile>` | No | Per-file conflict details |
| `hasComplexConflicts` | `boolean` | No | True if any complex conflicts exist |
| `summary` | `String` | No | Human-readable summary |

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
| `verdict` | `Verdict` | No | PASS/WARN/FAIL |
| `confidence` | `double` | No | 0.0–1.0 |
| `rationale` | `String` | No | Explanation of verdict |
| `findings` | `List<String>` | No | Specific findings/issues |

### ReviewReport

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `prContext` | `PrContext` | No | Full PR context |
| `rebaseResult` | `RebaseResult` | No | Rebase outcome |
| `conflictReport` | `ConflictReport` | No | Conflict analysis |
| `buildResult` | `BuildResult` | No | Build/test outcome |
| `assessments` | `List<AssessmentResult>` | No | All judge/assessment verdicts |
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

### DD-3: Three-tier judge cascade with deterministic-first ordering

**Context**: The Python system used 5 separate AI assessors with no gating between them.

**Decision**: T0 (BuildJudge, deterministic) → T1 (VersionPatternJudge, deterministic) → AI steps → T2 (QualityJudge, LLM). Failures at T0/T1 skip AI entirely.

**Alternatives considered**:
1. Run all judges in parallel — rejected because it wastes LLM tokens when deterministic checks fail
2. Single composite judge — rejected because it hides which tier caught the issue

**Rationale**: Deterministic checks are fast and free. LLM calls are slow and expensive. Gate on deterministic first. The cascade is also the core AgentWorks teaching story for the workshop.

### DD-4: Conflict detection without auto-resolution

**Context**: The Python system attempted auto-resolution of conflicts, which was fragile.

**Decision**: Detect and classify conflicts (simple vs complex) but do not resolve them. Report classification clearly.

**Alternatives considered**:
1. Full auto-resolution — rejected because it's fragile, hard to teach, and rarely succeeds on complex conflicts
2. No conflict handling at all — rejected because participants need to see conflict awareness in a real pipeline

**Rationale**: Classification is the valuable part. Participants see the pipeline acknowledge conflicts and make intelligent decisions (skip tests on complex conflicts) without the fragility of auto-resolution.

### DD-5: Pre-recorded journal fallback for workshop reliability

**Context**: Live execution depends on GitHub API, Claude Code CLI, and network. Any can fail during a live workshop.

**Decision**: Ship a pre-recorded journal (`fallback/pr-5774-journal.jsonl`) that the pipeline can replay if live execution fails.

**Rationale**: Workshop must work even on conference WiFi. The fallback journal contains a real execution diary that demonstrates all pipeline features. The "live vs fallback" distinction is visible in the report header.

---

## Error Handling Strategy

- **Steps**: Throw on unrecoverable errors. The workflow catches, logs to Journal, and produces a partial report.
- **Judges**: Return FAIL verdict with rationale. Never throw — a judge failure is a verdict, not an exception.
- **GitHub API**: Retry once on 5xx. On rate limit (403), check remaining quota and fail fast with clear message.
- **AgentClient**: Timeout at 120s. On failure, the AI step returns a "skipped" AssessmentResult and the workflow continues to report generation.
- **Git operations**: Capture stderr. On rebase conflict, return ConflictReport instead of throwing.

## Testing Strategy

- **Unit tests**: All judges with known inputs → expected verdicts. Domain model construction/serialization. Module discovery logic.
- **WireMock**: GitHub REST API integration tests with recorded JSON fixtures.
- **Mocked AgentClient**: AI step tests with canned responses — no live Claude Code calls in tests.
- **ArchUnit**: Layered architecture (steps don't import judges, judges don't import steps, github doesn't import steps). Naming conventions enforced.
- **JaCoCo**: 70% line coverage target.
- **No live integration tests in CI**: Live GitHub API and Claude Code tests exist but are `@Disabled` by default. Enabled manually for smoke testing.

## Open Questions

1. What is the exact `Judge` interface in `agent-judge-core` 0.9.1? Need to verify `evaluate()` signature and `Verdict` model during Step 1.0.
2. Does `JudgeGate` in `workflow-core` support WARN pass-through, or only binary PASS/FAIL? May need a custom gate adapter.
3. Should the report template use Mustache or Thymeleaf? Mustache is simpler but Thymeleaf has Spring Boot auto-configuration.
4. Is `agent-claude` 0.11.0 the right dependency for Claude Code CLI integration, or should we use `claude-code-sdk` 1.0.0 directly?

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-04-08T18:00-04:00 | Initial draft | Project creation |
