# Roadmap: AgentWorks PR Review Pipeline

> **Created**: 2026-04-08T17:00-04:00
> **Last updated**: 2026-04-08T17:30-04:00
> **Source plan**: Python PR review pipeline rewrite using AgentWorks stack

## Overview

Rewrite the Python PR review/merge pipeline (pr-review/, ~20 scripts, 832KB) as a Java project using the AgentWorks stack (workflow-core, journal-core, agent-judge, agent-client). The goal is a production-ready, workshop-teachable PR review pipeline that participants can run live at a Spring conference. Implementation follows dependency order: foundation and quality infrastructure first (Stage 1), then deterministic context gathering with no AI (Stage 2), AI assessment with judges (Stage 3), and finally report generation with workshop polish (Stage 4).

The judge cascade is strictly ordered: **T0 (BuildJudge, deterministic) → T1 (VersionPatternJudge, deterministic) → T2 (QualityJudge, LLM)**. T2 only fires if T0 and T1 pass or return warnings — never on failures. Deterministic checks always before LLM spend.

> **Before every commit**: Verify ALL exit criteria for the current step are met — especially the standard items (see [Step Exit Criteria Convention](#step-exit-criteria-convention)). Do NOT remove exit criteria to mark a step complete — fulfill them.

## Key Dependencies

| Artifact | Version | GroupId | Purpose |
|----------|---------|---------|---------|
| `agentworks-bom` | 1.0.4 | `io.github.markpollack` | Unified dependency management |
| `workflow-core` | 0.3.0 | `io.github.markpollack` | Step<I,O>, Workflow DSL, AgentContext, gates |
| `journal-core` | 0.9.0 | `io.github.markpollack` | Run tracking, events, metrics, artifacts |
| `agent-judge-core` | 0.9.1 | `org.springaicommunity` | Judge framework (deterministic + LLM) |
| `agent-client-core` | 0.11.0 | `org.springaicommunity.agents` | AgentClient abstraction for Claude Code |

---

## Stage 1: Foundation

### Step 1.0: Codebase Review and Design Validation

**Entry criteria**:
- [ ] Read: Python source in `/tmp/prmerge/` — existing implementation reference
- [ ] Read: AgentWorks source in `~/projects/agentworks/` — API surface understanding
- [ ] Read: `workflow-core` Step<I,O>, Workflow, AgentContext APIs
- [ ] Read: `journal-core` Journal, Run, JournalEvent APIs
- [ ] Read: `agent-judge-core` Judge interface and verdict model
- [ ] Read: `agent-client-core` AgentClient interface

**Work items**:
- [ ] REVIEW Python pipeline's three-phase structure against AgentWorks capabilities
- [ ] VERIFY Step<I,O> interface fits each pipeline step's input/output contract
- [ ] VERIFY JudgeGate integration for the three-tier judge cascade (T0/T1/T2)
- [ ] VERIFY Journal event types cover needed observability (git ops, API calls, AI calls)
- [ ] DOCUMENT mapping: Python scripts → Java steps/judges/models
- [ ] IDENTIFY any AgentWorks API gaps that need workarounds

**Exit criteria**:
- [ ] Design validated against actual AgentWorks APIs
- [ ] Python-to-Java mapping documented
- [ ] Create: `plans/learnings/step-1.0-design-review.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Validated design mapping, API gap analysis

---

### Step 1.1: Project Scaffolding

**Entry criteria**:
- [ ] Step 1.0 complete
- [ ] Read: `plans/learnings/step-1.0-design-review.md` — prior step learnings

**Work items**:
- [ ] CREATE `agentworks-pr-review/` project directory at `~/projects/agentworks-pr-review/`
- [ ] CREATE `pom.xml` with:
  - `agentworks-bom` 1.0.4 as BOM import
  - `workflow-core`, `journal-core`, `agent-judge-core`, `agent-client-core` deps
  - Spring Boot 3.5+ parent (or Boot 4 if available)
  - `spring-web` for RestClient (GitHub API)
  - Java 21+ compiler settings
- [ ] CREATE source directory layout:
  ```
  src/main/java/com/tuvium/prreview/
  ├── PrReviewApplication.java
  ├── config/
  ├── steps/
  ├── judges/
  ├── github/
  └── model/
  ```
- [ ] CREATE `src/main/resources/application.yml` with default config (spring-ai repo, known PR)
- [ ] CREATE `.gitignore`
- [ ] VERIFY: `./mvnw compile -q` succeeds with empty source
- [ ] COMMIT

**Exit criteria**:
- [ ] Project compiles with Spring Boot main class only
- [ ] All AgentWorks dependencies resolve from local Maven repo
- [ ] Directory structure matches design
- [ ] Create: `plans/learnings/step-1.1-project-scaffolding.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Compilable empty project with AgentWorks dependencies

---

### Step 1.2: Quality Infrastructure

**Entry criteria**:
- [ ] Step 1.1 complete
- [ ] Read: `plans/learnings/step-1.1-project-scaffolding.md` — prior step learnings

**Work items**:
- [ ] CONFIGURE ArchUnit (layered architecture: steps/, judges/, github/, model/, config/)
- [ ] CONFIGURE ArchUnit naming conventions (Steps end in "Step", Judges end in "Judge")
- [ ] CONFIGURE ArchUnit no-cycle rule
- [ ] CONFIGURE JSpecify nullness annotations (`@NullMarked` on package-info.java)
- [ ] CONFIGURE JaCoCo code coverage (target: 70% line coverage)
- [ ] CONFIGURE spring-javaformat plugin
- [ ] CREATE initial ArchUnit test class
- [ ] VERIFY: `./mvnw verify` passes with quality tools active

**Exit criteria**:
- [ ] ArchUnit rules enforced on scaffold
- [ ] JaCoCo reporting configured
- [ ] JSpecify annotations in place
- [ ] Create: `plans/learnings/step-1.2-quality-infrastructure.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Build pipeline with quality tooling active

---

### Step 1.3: Domain Models

**Entry criteria**:
- [ ] Step 1.2 complete
- [ ] Read: `plans/learnings/step-1.2-quality-infrastructure.md` — prior step learnings
- [ ] Read: Python `pr_context_collector.py` — data model reference

**Work items**:
- [ ] CREATE `PrContext.java` record hierarchy:
  - PR metadata (number, title, description, author, labels, state)
  - File changes (filename, status, additions, deletions, patch)
  - Comments and reviews
  - Linked issues
- [ ] CREATE `AssessmentResult.java` — judge verdict model (verdict enum, confidence, rationale, judge name)
- [ ] CREATE `ReviewReport.java` — final report model (PR context, assessment results, build status, recommendations)
- [ ] CREATE `BuildResult.java` — compilation/test result (success, module, output, duration)
- [ ] CREATE `ConflictInfo.java` — conflict detection result (conflicted files, classification: simple/complex)
- [ ] WRITE unit tests for record construction, equality, serialization
- [ ] VERIFY: `./mvnw test` passes

**Exit criteria**:
- [ ] All domain models are Java records with JSpecify annotations
- [ ] Tests pass for model construction and serialization
- [ ] Create: `plans/learnings/step-1.3-domain-models.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Immutable domain model records with tests

---

### Step 1.4: Test Infrastructure

**Entry criteria**:
- [ ] Step 1.3 complete
- [ ] Read: `plans/learnings/step-1.3-domain-models.md` — prior step learnings

**Work items**:
- [ ] CREATE test fixtures: `TestPrContexts.java` with factory methods for sample PR contexts
- [ ] CREATE test fixtures: `TestAssessments.java` with sample judge verdicts
- [ ] CREATE `src/test/resources/fixtures/` with sample JSON payloads (PR API responses)
- [ ] CREATE `fallback/pr-5774-journal.jsonl` — pre-recorded journal for workshop fallback
- [ ] VERIFY: `./mvnw test` passes

**Exit criteria**:
- [ ] Test fixtures available for all domain models
- [ ] Sample JSON fixtures for GitHub API responses
- [ ] Create: `plans/learnings/step-1.4-test-infrastructure.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Test fixtures and sample data

---

### Step 1.5: Stage 1 Consolidation

**Entry criteria**:
- [ ] All Stage 1 steps complete (1.0–1.4)
- [ ] Read: all `plans/learnings/step-1.*` files from this stage

**Work items**:
- [ ] COMPACT learnings from all Stage 1 steps into `plans/learnings/LEARNINGS.md`
  - Key discoveries that changed the approach
  - Patterns established during implementation
  - Deviations from design with rationale
  - Common pitfalls to avoid in future stages
- [ ] UPDATE `CLAUDE.md` with distilled learnings from the full stage
- [ ] VERIFY: `./mvnw verify` passes (full build with quality checks)

**Exit criteria**:
- [ ] `LEARNINGS.md` updated with compacted summary covering Stage 1
- [ ] Create: `plans/learnings/step-1.5-stage1-summary.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

---

## Stage 2: Deterministic Context Gathering (No AI)

### Step 2.0: Stage 2 Entry — Review and Context Load

**Entry criteria** *(inter-stage gate — do not skip)*:
- [ ] Stage 1 consolidation complete — Read: `plans/learnings/step-1.5-stage1-summary.md`
- [ ] Read: `plans/learnings/LEARNINGS.md` — full compacted project learnings

**Work items**:
- [ ] REVIEW Stage 1 summary for open questions or deferred decisions
- [ ] VERIFY AgentWorks API assumptions still hold
- [ ] DOCUMENT any scope changes discovered during review

**Exit criteria**:
- [ ] Stage 1 context loaded; no blocking issues unresolved
- [ ] Create: `plans/learnings/step-2.0-stage2-entry.md`
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Verified entry into Stage 2

---

### Step 2.1: GitHub REST Client

**Entry criteria**:
- [ ] Step 2.0 complete
- [ ] Read: `plans/learnings/step-2.0-stage2-entry.md` — prior step learnings
- [ ] Read: Python `github_rest_client.py` — implementation reference

**Work items**:
- [ ] CREATE `GitHubRestClient.java` using Spring's `RestClient`:
  - `getPr(int prNumber)` → PrContext
  - `getPrFiles(int prNumber)` → List<FileChange>
  - `getPrComments(int prNumber)` → List<Comment>
  - `getPrReviews(int prNumber)` → List<Review>
  - `getLinkedIssues(int prNumber)` → List<Issue>
  - Pagination support
  - Optional `GITHUB_TOKEN` header
- [ ] CREATE `@ConfigurationProperties("github")` for base URL, repo, token
- [ ] WRITE tests with WireMock (mock GitHub API responses using JSON fixtures from Step 1.4)
- [ ] VERIFY: unauthenticated access works against live spring-ai repo (smoke test, disabled by default)

**Exit criteria**:
- [ ] All GitHub API endpoints implemented and tested
- [ ] WireMock tests cover success, pagination, rate limiting
- [ ] `./mvnw test` passes
- [ ] Create: `plans/learnings/step-2.1-github-client.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: `GitHubRestClient` with full test coverage

---

### Step 2.2: FetchPrContext Step

**Entry criteria**:
- [ ] Step 2.1 complete
- [ ] Read: `plans/learnings/step-2.1-github-client.md` — prior step learnings

**Work items**:
- [ ] CREATE `FetchPrContext.java` implementing `Step<Integer, PrContext>`:
  - Takes PR number as input
  - Calls GitHubRestClient to assemble full PrContext
  - Logs to Journal (JournalEvent for API calls)
- [ ] WIRE into AgentWorkflow DSL
- [ ] WRITE unit test with mocked GitHubRestClient
- [ ] VERIFY: step executes in workflow context

**Exit criteria**:
- [ ] FetchPrContext produces complete PrContext from PR number
- [ ] Journal events logged for API calls
- [ ] `./mvnw test` passes
- [ ] Create: `plans/learnings/step-2.2-fetch-pr-context.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: `FetchPrContext` step with Journal integration

---

### Step 2.3: RebaseStep

**Entry criteria**:
- [ ] Step 2.2 complete
- [ ] Read: `plans/learnings/step-2.2-fetch-pr-context.md` — prior step learnings

**Work items**:
- [ ] CREATE `RebaseStep.java` implementing `Step<PrContext, RebaseResult>`:
  - `git fetch origin pull/{N}/head:{branch}`
  - `git checkout {branch}`
  - `git rebase main` (simplified — no intelligent squash)
  - Return RebaseResult (success/conflict/error, branch name)
- [ ] CREATE `RebaseResult.java` record (success flag, branch, error message if any)
- [ ] Log git operations to Journal (GitEvent)
- [ ] WRITE unit test with JGit or process mocking
- [ ] VERIFY: handles clean rebase and failed rebase scenarios

**Exit criteria**:
- [ ] RebaseStep handles rebase success and failure
- [ ] Git operations logged to Journal
- [ ] `./mvnw test` passes
- [ ] Create: `plans/learnings/step-2.3-rebase-step.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: `RebaseStep` with Journal logging

---

### Step 2.4: ConflictDetectionStep

**Entry criteria**:
- [ ] Step 2.3 complete
- [ ] Read: `plans/learnings/step-2.3-rebase-step.md` — prior step learnings
- [ ] Read: Python `conflict_analyzer.py` — conflict detection and classification reference

**Work items**:
- [ ] CREATE `ConflictDetectionStep.java` implementing `Step<RebaseResult, ConflictReport>`:
  - If rebase succeeded cleanly → return empty ConflictReport (no conflicts)
  - If rebase had conflicts → detect and classify each conflicted file:
    - **Simple**: whitespace, import ordering, version bumps (auto-resolvable)
    - **Complex**: logic changes, overlapping edits, structural refactors (needs human review)
  - Parse git conflict markers to determine classification
  - Return ConflictReport with per-file classification and clear human-readable message
- [ ] CREATE `ConflictReport.java` record (List<ConflictFile>, boolean hasComplexConflicts, summary message)
- [ ] CREATE `ConflictFile.java` record (path, classification enum SIMPLE/COMPLEX, description)
- [ ] Log conflict analysis to Journal
- [ ] WRITE unit tests with sample conflict markers for both simple and complex cases
- [ ] VERIFY: participants see conflict handling acknowledged in output even when no conflicts occur

**Exit criteria**:
- [ ] Conflict detection correctly classifies simple vs complex conflicts
- [ ] Clean rebase produces clear "no conflicts" message (not silent)
- [ ] Journal logs conflict analysis events
- [ ] `./mvnw test` passes
- [ ] Create: `plans/learnings/step-2.4-conflict-detection.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: `ConflictDetectionStep` with classification (simple/complex) and Journal logging

---

### Step 2.5: RunTestsStep

**Entry criteria**:
- [ ] Step 2.4 complete
- [ ] Read: `plans/learnings/step-2.4-conflict-detection.md` — prior step learnings
- [ ] Read: Python `test_discovery.py` — module discovery reference

**Work items**:
- [ ] CREATE `RunTestsStep.java` implementing `Step<ConflictReport, BuildResult>`:
  - Skip if complex conflicts detected (return BuildResult.skipped())
  - Discover affected Maven modules from changed files (port test_discovery.py logic)
  - Run `./mvnw test -pl {modules} -am` for targeted testing
  - Capture build output, success/failure, duration
  - Return BuildResult
- [ ] CREATE `ModuleDiscovery.java` — extracts affected modules from file paths
- [ ] Log build execution to Journal
- [ ] WRITE unit tests for module discovery logic
- [ ] WRITE integration test that verifies Maven command construction

**Exit criteria**:
- [ ] Module discovery correctly maps files to Maven modules
- [ ] Build execution captured with timing and status
- [ ] `./mvnw test` passes
- [ ] Create: `plans/learnings/step-2.5-run-tests.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: `RunTestsStep` with module discovery and Journal logging

---

### Step 2.6: BuildJudge (T0)

**Entry criteria**:
- [ ] Step 2.5 complete
- [ ] Read: `plans/learnings/step-2.5-run-tests.md` — prior step learnings

**Work items**:
- [ ] CREATE `BuildJudge.java` implementing agent-judge `Judge` interface:
  - T0 deterministic judge — no AI
  - Checks: compilation success, test pass rate, rebase clean, no complex conflicts
  - Returns verdict: PASS / WARN / FAIL with rationale
  - FAIL blocks all downstream steps (T1, T2, AI assessment)
  - WARN allows downstream but flags in report
- [ ] WIRE as JudgeGate in workflow (blocks Phase 2 AI steps if FAIL)
- [ ] WRITE unit tests with known BuildResult + ConflictReport inputs

**Exit criteria**:
- [ ] BuildJudge correctly evaluates build outcomes with three-state verdict
- [ ] JudgeGate integration tested (FAIL blocks, WARN passes, PASS passes)
- [ ] `./mvnw test` passes
- [ ] Create: `plans/learnings/step-2.6-build-judge.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: `BuildJudge` (T0 deterministic) with JudgeGate

---

### Step 2.7: Stage 2 Consolidation

**Entry criteria**:
- [ ] All Stage 2 steps complete (2.0–2.6)
- [ ] Read: all `plans/learnings/step-2.*` files from this stage

**Work items**:
- [ ] COMPACT learnings from all Stage 2 steps into `plans/learnings/LEARNINGS.md`
- [ ] UPDATE `CLAUDE.md` with distilled learnings from Stage 2
- [ ] VERIFY: `./mvnw verify` passes

**Exit criteria**:
- [ ] `LEARNINGS.md` updated with compacted summary covering Stages 1–2
- [ ] Create: `plans/learnings/step-2.7-stage2-summary.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

---

## Stage 3: AI Assessment Pipeline

Judge cascade: T0 (Stage 2) → **T1 (VersionPatternJudge, deterministic, this stage)** → AI steps → **T2 (QualityJudge, LLM)**. T1 runs first in this stage because it's deterministic — no LLM spend if version patterns are broken. T2 fires only if T0 and T1 are PASS or WARN.

### Step 3.0: Stage 3 Entry — Review and Context Load

**Entry criteria** *(inter-stage gate — do not skip)*:
- [ ] Stage 2 consolidation complete — Read: `plans/learnings/step-2.7-stage2-summary.md`
- [ ] Read: `plans/learnings/LEARNINGS.md` — full compacted project learnings

**Work items**:
- [ ] REVIEW Stage 2 summary for API patterns, Journal event conventions
- [ ] VERIFY AgentClient API surface for Claude Code integration
- [ ] DOCUMENT prompt template strategy

**Exit criteria**:
- [ ] Stage 2 context loaded; AI integration approach confirmed
- [ ] Create: `plans/learnings/step-3.0-stage3-entry.md`
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Verified entry into Stage 3

---

### Step 3.1: VersionPatternJudge (T1)

**Entry criteria**:
- [ ] Step 3.0 complete
- [ ] Read: `plans/learnings/step-3.0-stage3-entry.md` — prior step learnings

**Work items**:
- [ ] CREATE `VersionPatternJudge.java` — T1 deterministic judge:
  - Scans diff for Boot 3→4 migration anti-patterns
  - Checks: deprecated API usage, javax→jakarta, property renames
  - Checks: `@WebMvcTest`/`@DataJpaTest` package moves (Boot 4)
  - Checks: `MockMvc` → `MockMvcTester` migration
  - No AI — pure pattern matching on diff content
  - Returns verdict: PASS / WARN / FAIL with specific pattern matches cited
  - FAIL blocks AI assessment steps (no LLM spend on broken version patterns)
- [ ] WIRE as JudgeGate after BuildJudge(T0), before AI steps
- [ ] WRITE unit tests with known Boot 3→4 anti-patterns in sample diffs
- [ ] WRITE unit tests with clean diffs (no patterns matched → PASS)

**Exit criteria**:
- [ ] VersionPatternJudge detects known Boot 3→4 anti-patterns
- [ ] JudgeGate blocks AI steps on FAIL
- [ ] `./mvnw test` passes
- [ ] Create: `plans/learnings/step-3.1-version-pattern-judge.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: `VersionPatternJudge` (T1 deterministic) — highest-value deterministic judge

---

### Step 3.2: Prompt Templates

**Entry criteria**:
- [ ] Step 3.1 complete
- [ ] Read: `plans/learnings/step-3.1-version-pattern-judge.md` — prior step learnings
- [ ] Read: Python `ai_risk_assessor.py`, `solution_assessor.py`, `backport_assessor.py` — prompt reference

**Work items**:
- [ ] CREATE `src/main/resources/prompts/code-quality-assessment.md` — quality review prompt
- [ ] CREATE `src/main/resources/prompts/backport-assessment.md` — backport candidacy prompt
- [ ] DESIGN structured output format (JSON schema for judge consumption)
- [ ] VERIFY prompts are parameterized with PR context placeholders

**Exit criteria**:
- [ ] Prompt templates created with clear structure
- [ ] Output format documented for downstream QualityJudge consumption
- [ ] Create: `plans/learnings/step-3.2-prompt-templates.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Prompt templates in `src/main/resources/prompts/`

---

### Step 3.3: AssessCodeQuality Step

**Entry criteria**:
- [ ] Step 3.2 complete
- [ ] Read: `plans/learnings/step-3.2-prompt-templates.md` — prior step learnings
- [ ] T0 (BuildJudge) and T1 (VersionPatternJudge) must be PASS or WARN — gated by workflow

**Work items**:
- [ ] CREATE `AssessCodeQuality.java` implementing `Step<PrContext, AssessmentResult>`:
  - Uses AgentClient to call Claude Code with quality prompt
  - Passes PR diff, context, and changed files
  - Parses structured JSON response into AssessmentResult
  - Logs LLMCallEvent to Journal
- [ ] WRITE unit test with mocked AgentClient
- [ ] VERIFY: step produces valid AssessmentResult

**Exit criteria**:
- [ ] Quality assessment produces structured verdicts
- [ ] Journal logs LLM call events
- [ ] `./mvnw test` passes
- [ ] Create: `plans/learnings/step-3.3-code-quality.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: `AssessCodeQuality` step with AgentClient integration

---

### Step 3.4: AssessBackport Step

**Entry criteria**:
- [ ] Step 3.3 complete
- [ ] Read: `plans/learnings/step-3.3-code-quality.md` — prior step learnings

**Work items**:
- [ ] CREATE `AssessBackport.java` implementing `Step<PrContext, AssessmentResult>`:
  - Uses AgentClient with backport prompt
  - Evaluates: breaking changes, API compatibility, maintenance branch impact
  - Returns backport candidacy verdict
- [ ] WRITE unit test with mocked AgentClient
- [ ] VERIFY: correct backport/no-backport recommendations

**Exit criteria**:
- [ ] Backport assessment produces structured verdicts
- [ ] `./mvnw test` passes
- [ ] Create: `plans/learnings/step-3.4-backport.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: `AssessBackport` step

---

### Step 3.5: QualityJudge (T2)

**Entry criteria**:
- [ ] Step 3.4 complete
- [ ] Read: `plans/learnings/step-3.4-backport.md` — prior step learnings

**Work items**:
- [ ] CREATE `QualityJudge.java` — T2 LLM judge:
  - Evaluates AI assessment quality/confidence
  - Cross-checks code quality and backport assessments for consistency
  - Flags low-confidence or contradictory AI assessments
  - Uses AgentClient for LLM evaluation
  - Returns verdict: PASS / WARN / FAIL with rationale
- [ ] WIRE complete three-tier cascade: BuildJudge(T0) → VersionPatternJudge(T1) → [AI steps] → QualityJudge(T2)
- [ ] WRITE unit test with mocked AgentClient
- [ ] VERIFY: T2 only runs after T0 and T1 pass/warn

**Exit criteria**:
- [ ] Three-tier judge cascade fully wired and tested
- [ ] T0 and T1 are fully deterministic (no AI)
- [ ] T2 uses AgentClient for LLM evaluation, only fires after T0+T1 pass/warn
- [ ] `./mvnw test` passes
- [ ] Create: `plans/learnings/step-3.5-quality-judge.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: `QualityJudge` (T2 LLM), complete three-tier cascade

---

### Step 3.6: Stage 3 Consolidation

**Entry criteria**:
- [ ] All Stage 3 steps complete (3.0–3.5)
- [ ] Read: all `plans/learnings/step-3.*` files from this stage

**Work items**:
- [ ] COMPACT learnings from all Stage 3 steps into `plans/learnings/LEARNINGS.md`
- [ ] UPDATE `CLAUDE.md` with distilled learnings from Stage 3
- [ ] VERIFY: `./mvnw verify` passes

**Exit criteria**:
- [ ] `LEARNINGS.md` updated with compacted summary covering Stages 1–3
- [ ] Create: `plans/learnings/step-3.6-stage3-summary.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

---

## Stage 4: Report Generation and Workshop Polish

### Step 4.0: Stage 4 Entry — Review and Context Load

**Entry criteria** *(inter-stage gate — do not skip)*:
- [ ] Stage 3 consolidation complete — Read: `plans/learnings/step-3.6-stage3-summary.md`
- [ ] Read: `plans/learnings/LEARNINGS.md` — full compacted project learnings

**Work items**:
- [ ] REVIEW full pipeline: context → rebase → conflict detect → test → T0 → T1 → assess → T2 → report
- [ ] VERIFY end-to-end data flow through all steps
- [ ] DOCUMENT any remaining integration issues

**Exit criteria**:
- [ ] Full pipeline reviewed; integration approach confirmed
- [ ] Create: `plans/learnings/step-4.0-stage4-entry.md`
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Verified entry into Stage 4

---

### Step 4.1: GenerateReport Step

**Entry criteria**:
- [ ] Step 4.0 complete
- [ ] Read: `plans/learnings/step-4.0-stage4-entry.md` — prior step learnings
- [ ] Read: Python `enhanced_report_generator.py`, `single_pr_html_generator.py` — report reference

**Work items**:
- [ ] CREATE `src/main/resources/templates/report.md` — Mustache/Thymeleaf report template
- [ ] CREATE `GenerateReport.java` implementing `Step<ReviewReport, Path>`:
  - Assembles all judge verdicts + PR context into ReviewReport
  - Renders markdown report from template
  - Optionally renders HTML dashboard
  - Writes to `reports/review-pr-{N}.md` and `.html`
- [ ] WRITE unit test with sample ReviewReport → expected markdown output
- [ ] VERIFY: report includes all three judge tier verdicts

**Exit criteria**:
- [ ] Reports render with complete information
- [ ] Both markdown and HTML output supported
- [ ] `./mvnw test` passes
- [ ] Create: `plans/learnings/step-4.1-report-generation.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: `GenerateReport` step with markdown/HTML output

---

### Step 4.2: PrReviewWorkflow Composition

**Entry criteria**:
- [ ] Step 4.1 complete
- [ ] Read: `plans/learnings/step-4.1-report-generation.md` — prior step learnings

**Work items**:
- [ ] CREATE `PrReviewWorkflow.java` — Workflow DSL composition:
  ```
  Workflow.builder()
    // Phase 1: Deterministic context gathering
    .step(fetchPrContext)
    .step(rebaseStep)
    .step(conflictDetectionStep)
    .step(runTestsStep)
    .gate(buildJudge)              // T0: compile + test + no complex conflicts
    // Phase 2: Deterministic check, then AI assessment
    .gate(versionPatternJudge)     // T1: Boot 3→4 patterns (deterministic, before LLM spend)
    .step(assessCodeQuality)       // AI via AgentClient
    .step(assessBackport)          // AI via AgentClient
    .gate(qualityJudge)            // T2: LLM meta-judge (only if T0+T1 pass/warn)
    // Phase 3: Report generation
    .step(generateReport)
    .build()
  ```
- [ ] CREATE `WorkshopConfig.java` — `@ConfigurationProperties("workshop")`:
  - Default PR number, repo, timeout settings
  - Skip-AI flag for deterministic-only demos
  - Journal output directory
- [ ] WIRE all steps and judges via Spring dependency injection
- [ ] WRITE integration test: full workflow with mocked GitHub API and AgentClient
- [ ] VERIFY: Journal captures complete run diary

**Exit criteria**:
- [ ] Full workflow composes and executes end-to-end
- [ ] Journal diary is human-readable
- [ ] `./mvnw test` passes
- [ ] Create: `plans/learnings/step-4.2-workflow-composition.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: `PrReviewWorkflow` with full pipeline composition

---

### Step 4.3: Pre-flight Check and Workshop Runner

**Entry criteria**:
- [ ] Step 4.2 complete
- [ ] Read: `plans/learnings/step-4.2-workflow-composition.md` — prior step learnings

**Work items**:
- [ ] IMPLEMENT pre-flight check command:
  ```bash
  ./mvnw spring-boot:run -Dspring-boot.run.arguments="--check"
  ```
  Checks: Java version, Git, Claude Code CLI, GitHub API access, **rate limit headroom** (critical for multiple participants), **GITHUB_TOKEN presence and validity**
- [ ] CREATE fallback mode: use pre-recorded journal (`fallback/pr-5774-journal.jsonl`) if live execution fails
- [ ] CREATE "point at your own repo" config override (`--github.repo=your/repo --workshop.pr=123`)
- [ ] WRITE smoke test: pre-flight check in CI
- [ ] VERIFY: runs end-to-end against spring-ai PR #5774

**Exit criteria**:
- [ ] Pre-flight check validates all prerequisites including GITHUB_TOKEN and rate limits
- [ ] Fallback journal works when live API unavailable
- [ ] Custom repo override works
- [ ] `./mvnw test` passes
- [ ] Create: `plans/learnings/step-4.3-workshop-runner.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Workshop-ready runner with pre-flight and fallback

---

### Step 4.4: Stage 4 Consolidation and Final Review

**Entry criteria**:
- [ ] All Stage 4 steps complete (4.0–4.3)
- [ ] Read: all `plans/learnings/step-4.*` files from this stage

**Work items**:
- [ ] COMPACT learnings from all Stage 4 steps into `plans/learnings/LEARNINGS.md`
- [ ] UPDATE `CLAUDE.md` with final project learnings
- [ ] RUN full end-to-end against spring-ai PR #5774 (live)
- [ ] VERIFY: Journal diary suitable for workshop opening moment
- [ ] VERIFY: Report quality matches Python pipeline output
- [ ] DOCUMENT: workshop setup instructions in README.md

**Exit criteria**:
- [ ] `LEARNINGS.md` covers all four stages
- [ ] End-to-end live run succeeds
- [ ] Workshop instructions complete
- [ ] `./mvnw verify` passes (full build with all quality checks)
- [ ] Create: `plans/learnings/step-4.4-stage4-summary.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

---

## Plans Directory Structure

```
plans/
├── ROADMAP.md                                 # This file
├── learnings/
│   ├── LEARNINGS.md                           # Tier 1: Compacted summary
│   ├── step-1.0-design-review.md
│   ├── step-1.1-project-scaffolding.md
│   ├── step-1.2-quality-infrastructure.md
│   ├── step-1.3-domain-models.md
│   ├── step-1.4-test-infrastructure.md
│   ├── step-1.5-stage1-summary.md
│   ├── step-2.0-stage2-entry.md
│   ├── step-2.1-github-client.md
│   ├── step-2.2-fetch-pr-context.md
│   ├── step-2.3-rebase-step.md
│   ├── step-2.4-conflict-detection.md
│   ├── step-2.5-run-tests.md
│   ├── step-2.6-build-judge.md
│   ├── step-2.7-stage2-summary.md
│   ├── step-3.0-stage3-entry.md
│   ├── step-3.1-version-pattern-judge.md
│   ├── step-3.2-prompt-templates.md
│   ├── step-3.3-code-quality.md
│   ├── step-3.4-backport.md
│   ├── step-3.5-quality-judge.md
│   ├── step-3.6-stage3-summary.md
│   ├── step-4.0-stage4-entry.md
│   ├── step-4.1-report-generation.md
│   ├── step-4.2-workflow-composition.md
│   ├── step-4.3-workshop-runner.md
│   └── step-4.4-stage4-summary.md
```

---

## Conventions

### Commit Convention

Every step ends with a git commit:
```
Step X.Y: Brief description of what was done
```

### Step Entry Criteria Convention

Every step's entry criteria must include:
```markdown
- [ ] Previous step complete
- [ ] Read: `plans/learnings/step-{PREV}-{topic}.md` — prior step learnings
```

### Step Exit Criteria Convention

Every step's exit criteria must include:
```markdown
- [ ] All tests pass: `./mvnw test`
- [ ] Create: `plans/learnings/step-X.Y-topic.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT
```

### Stage Consolidation Convention

Last step of each stage compacts all per-step learnings into `LEARNINGS.md` and creates a stage summary.

### Inter-Stage Gate Convention

First step of Stage N (N > 1) gates on Stage N-1 consolidation and reads `LEARNINGS.md`.

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-04-08T17:00-04:00 | Initial draft | Python-to-Java rewrite plan |
| 2026-04-08T17:30-04:00 | Add ConflictDetectionStep (2.4), reorder Stage 3 (T1 before AI), fix BOM to 1.0.4 | User feedback |
