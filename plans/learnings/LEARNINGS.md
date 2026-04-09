# Learnings: AgentWorks PR Review Pipeline

> **Last compacted**: 2026-04-09T08:00-04:00
> **Covers through**: Stage 3 complete (Steps 1.0–3.5)

This is the **Tier 1 compacted summary**. Read this first for the current state of project knowledge. For details on specific steps, see the per-step files (Tier 2).

---

## Key Discoveries

### From Python System (pre-implementation)

1. **AgentJournal is non-negotiable from step 1** — Claude Code subprocess calls without instrumentation produce no diagnosable output. Journal integration must be wired into every step from the start.
2. **Version pattern check is the highest-value deterministic judge** — Boot 3→4 migration patterns catch real issues that LLM assessment misses. T1 runs before any LLM spend.
3. **GitHub API rate limits hit fast with multiple participants** — 60 req/hr unauthenticated. GITHUB_TOKEN must be in pre-flight check as hard requirement.

### From API/Source Validation (Step 1.0)

4. **Two AgentClient interfaces** — workflow-flows has simple `String execute(String, AgentContext)`; agent-client-core has full fluent builder. `AgentClientStep.of()` uses the workflow-flows one.
5. **JudgeGate does NOT bridge AgentContext → JudgmentContext** — need custom `PrReviewGate` (DD-8) to populate `JudgmentContext.metadata()`.
6. **JudgmentStatus: PASS/FAIL/ABSTAIN/ERROR** — no WARN. Use `NumericalScore` + `TieredGate` (ESCALATE) for warnings.
7. **CascadedJury preserves per-tier verdicts** — `verdict.subVerdicts()` with `individualByName` supports the workshop teaching story.
8. **Journal default is InMemoryStorage** — must explicitly configure `JsonFileStorage` for persistence.

### From Implementation (Steps 1.1–1.4)

9. **Record factory method name clash** — Java record component accessor `skipped()` conflicts with static factory `skipped()` returning `BuildResult`. Use different verb: `skippedBuild()`.
10. **ArchUnit 1.4.x `failOnEmptyShould` defaults to true** — naming rules on empty packages need `allowEmptyShould(true)`.
11. **github-collector as reference** — `~/tuvium/projects/github-collector` has production GitHub REST DTOs with SNAKE_CASE ObjectMapper, Author record, separate issue/review comment endpoints.
12. **JSON fixtures use raw API format** — snake_case, nested `user.login`, `base.ref`. The `GitHubRestClient` parsing layer flattens into our domain model.

### From Stage 2: Deterministic Context Gathering (Steps 2.0–2.6)

13. **Boot 4 uses Jackson 3.x** — `tools.jackson.databind.*`, NOT `com.fasterxml.jackson.databind.*`. Jackson 2.x is still on classpath transitively so code compiles but fails at runtime. Always use `tools.jackson.databind.JsonNode`.
14. **JudgmentContext.metadata() rejects null values** — `builder().metadata(key, value)` throws NPE on null. Guard with null check before calling.
15. **ArchUnit naming rules should scope to public classes** — Package-private utilities (e.g., `ModuleDiscovery`) don't need naming convention enforcement. Add `.arePublic()` to the predicate.
16. **ProcessBuilder over JGit for git operations** — Direct CLI is simpler and more workshop-readable. `ProcessResult` inner record keeps it clean.
17. **Conflict classification by filename pattern, not markers** — Simpler and sufficient for workshop. Five patterns cover build/config files as SIMPLE; everything else COMPLEX.
18. **Module discovery via `/src/` marker** — `filePath.indexOf("/src/")` reliably finds Maven module boundary. Root files map to `.`.
19. **Judge is @FunctionalInterface** — `Judgment judge(JudgmentContext context)`. Use `Check` sub-assertions for granular reporting. `Judgment.builder()` requires explicit score, status, reasoning, checks.
20. **JudgeGate confirmed: only passes output.toString()** — Custom `PrReviewGate` (DD-8) needed to bridge `AgentContext` → `JudgmentContext.metadata()` with structured domain objects.

### From Stage 3: AI Assessment Pipeline (Steps 3.0–3.5)

21. **VersionPatternJudge scans added lines only** — `^\\+` prefix ensures removed lines don't trigger false positives. Five patterns: javax, jackson-2x, @MockBean, MockMvcRequestBuilders, WebSecurityConfigurerAdapter.
22. **Regex-based JSON parsing for AI responses** — `AssessmentParser` avoids Jackson coupling in the response parsing path. Handles malformed responses gracefully with ERROR status.
23. **AgentGeneration, not AgentResult** — `new AgentResponse(List.of(new AgentGeneration(text)))` for constructing test responses. No `AgentResult` class exists in agent-client-core.
24. **QualityJudge uses NumericalScore** — Weighted composite (70% quality + 30% backport). TieredGate compatible for PASS/ESCALATE/FAIL at workflow level.
25. **AgentClient from agent-client-core** — `org.springaicommunity.agents.client.AgentClient` (fluent API). `run(String)` convenience method returns `AgentClientResponse.getResult()` as String.

## Patterns Established

| Pattern | Detail |
|---------|--------|
| **Step<I,O> lives in `workflow-flows`** | Not `workflow-core`. High-level DSL: Step, Workflow, AgentContext, ContextKey, JudgeGate, TieredGate |
| **AgentContext as data bus** | Immutable accumulator with `ContextKey<T>`. Steps publish via `updateContext()`, executor auto-publishes under `Steps.outputOf(stepName)` |
| **Defensive list copying** | All records with list fields use `List.copyOf()` in compact constructors — defensive + null-rejecting |
| **Judgment convenience factories** | `Judgment.pass(String)`, `.fail(String)`, `.abstain(String)` — auto-sets `BooleanScore`, takes only reasoning |
| **config/ stays pure** | `@ConfigurationProperties` records only. Wiring class (`PrReviewWorkflow`) at top-level package, not in config/ |
| **spring-javaformat at validate phase** | Run `./mvnw spring-javaformat:apply` before commit. Plugin at validate phase catches formatting before compile |
| **Test fixture conventions** | `TestPrContexts` / `TestAssessments` in test model package. JSON fixtures in `src/test/resources/fixtures/` |
| **Step side-channel data via ContextKey** | `FetchPrContextStep.PR_CONTEXT` — public static constant on producing step. Downstream reads via `ctx.require(KEY)` |
| **ProcessBuilder for CLI steps** | Configurable `workingDirectory(Path)`, inner `ProcessResult(exitCode, stdout, stderr)` record, error returns domain result (not exception) |
| **WireMock for REST client tests** | WireMock 3.13.1, `@WireMockTest` annotation, JSON fixture stubs for all GitHub API endpoints |
| **Judge Check sub-assertions** | `Check.pass(name)` / `Check.fail(name, message)` for granular reporting within a single `Judgment` |

## Deviations from Design

| Design says | Implementation does | Why |
|-------------|-------------------|-----|
| `BuildResult.skipped()` | `BuildResult.skippedBuild()` | Record accessor name clash |
| `ContextKeys.java` in Step 1.3 | ContextKey constants on producing steps | Each step defines its own public static ContextKey |
| Fallback journal in Step 1.4 | Deferred to Stage 4 | Can't validate format until pipeline produces real events |
| Parse git conflict markers | Classify by filename pattern | Simpler, sufficient for workshop |
| JudgeGate wiring in Step 2.6 | Deferred to Step 4.2 | DD-8: need custom gate for structured metadata |
| Journal logging in Steps 2.2–2.5 | Deferred to journal wiring step | Journal infrastructure not yet built |

## Common Pitfalls

1. **Don't use `gh` CLI** — Broadcom SAML SSO blocks OAuth tokens for spring-projects org. Use direct REST API.
2. **JudgeGate metadata gap** — Built-in `JudgeGate` only passes `output.toString()` to judges. Custom gate needed for structured data.
3. **Two AgentClient interfaces** — Don't confuse workflow-flows' vs agent-client-core's. Different signatures, different packages.
4. **`Judgment.metadata` serialization** — `Map<String, Object>` with `Map.copyOf()` — may need custom handling if values aren't serializable.
5. **GitHub API comment endpoints** — Issue comments (`/issues/{n}/comments`) vs review comments (`/pulls/{n}/comments`) are different. Our model flattens both into `Comment`.

## Dependency Tree (AgentWorks subset)

```
workflow-flows:0.3.0
├── workflow-core:0.3.0
│   └── workflow-api:0.3.0
│       └── spring-ai-agent-utils:0.6.0
└── spring-ai-client-chat:2.0.0-M3

journal-core:0.9.0
agent-judge-core:0.9.1
agent-client-core:0.11.0 → agent-model:0.11.0 → agent-sandbox-core:0.9.1
agent-claude:0.11.0 (runtime)
```

## Stage 3 Setup Notes

- `PrReviewGate` (DD-8) custom gate — bridges `AgentContext` → `JudgmentContext.metadata()`. Highest-risk custom code for workflow composition.
- `GenerateReport` must handle absent AI assessments via `ctx.get()` → `Optional.empty()`
- VersionPatternJudge (T1) — deterministic, scans diff for Boot 3→4 anti-patterns. Runs before any LLM spend.
- AgentClient integration: workflow-flows' simple interface vs agent-client-core's fluent builder — choose based on prompt complexity.

---

## Per-Step Detail Files (Tier 2)

| File | Step | Topic |
|------|------|-------|
| `step-1.0-design-review.md` | 1.0 | API validation, 43 claims, DD-6 through DD-8 |
| `step-1.1-project-scaffolding.md` | 1.1 | pom.xml, dependency tree, Boot 4.0.3 |
| `step-1.2-quality-infrastructure.md` | 1.2 | ArchUnit rules, JaCoCo, spring-javaformat |
| `step-1.3-domain-models.md` | 1.3 | 12 records, factory method clash, defensive copying |
| `step-1.4-test-infrastructure.md` | 1.4 | Test fixtures, JSON payloads, github-collector reference |
| `step-2.0-stage2-entry.md` | 2.0 | Stage entry review, github-collector decision |
| `step-2.1-github-client.md` | 2.1 | Spring RestClient, Jackson 3.x, WireMock, rate limits |
| `step-2.2-fetch-pr-context.md` | 2.2 | Step<I,O> template, ContextKey pattern |
| `step-2.3-rebase-step.md` | 2.3 | ProcessBuilder, git CLI, configurable working dir |
| `step-2.4-conflict-detection.md` | 2.4 | Filename-pattern classification, SIMPLE vs COMPLEX |
| `step-2.5-run-tests.md` | 2.5 | Module discovery, ArchUnit public-only naming rule |
| `step-2.6-build-judge.md` | 2.6 | Judge API, Check sub-assertions, metadata key pattern |
| `step-3.6-stage3-summary.md` | 3.6 | Full Stage 3: T1, AI steps, T2, AgentClient API |

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-04-08T17:30-04:00 | Seeded with three key learnings from Python system | Pre-implementation knowledge transfer |
| 2026-04-08T19:00-04:00 | Added API patterns from source exploration | Review feedback |
| 2026-04-08T19:30-04:00 | Added docs comparison findings | Docs comparison |
| 2026-04-08T20:00-04:00 | Added source validation findings (4 parallel agents) | Source code validation |
| 2026-04-08T22:45-04:00 | **Stage 1 consolidation** — compacted Steps 1.0–1.4 | Step 1.5 |
| 2026-04-08T23:30-04:00 | **Stage 2 consolidation** — compacted Steps 2.0–2.6 | Step 2.7 |
| 2026-04-09T08:00-04:00 | **Stage 3 consolidation** — compacted Steps 3.0–3.5 | Step 3.6 |
