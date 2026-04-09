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
- All in `com.tuvium.prreview.model` package, all Java records
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

## Architecture
Three-phase pipeline:
1. **Deterministic Context Gathering** — GitHub API, git rebase, conflict detection, tests
2. **AI Assessment** — Code quality + backport assessment via AgentClient
3. **Report Generation** — Markdown/HTML from judge verdicts

Judge cascade: **T0 (BuildJudge)** → **T1 (VersionPatternJudge)** → AI steps → **T2 (QualityJudge)**
- T0/T1 are deterministic (no AI)
- T2 only fires if T0 and T1 pass or warn

## Python Reference
- Original Python pipeline: `~/projects/spring-ai-project-mgmt/pr-review/`
- Portable copy: `/tmp/prmerge/`
