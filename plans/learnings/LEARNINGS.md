# Learnings: AgentWorks PR Review Pipeline

> **Last compacted**: 2026-04-08T22:45-04:00
> **Covers through**: Stage 1 complete (Steps 1.0–1.4)

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

## Deviations from Design

| Design says | Implementation does | Why |
|-------------|-------------------|-----|
| `BuildResult.skipped()` | `BuildResult.skippedBuild()` | Record accessor name clash |
| `ContextKeys.java` in Step 1.3 | Deferred to Step 2.x | Can't define meaningful keys until steps exist |
| Fallback journal in Step 1.4 | Deferred to Stage 4 | Can't validate format until pipeline produces real events |

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

## Stage 2 Setup Notes

- `GitHubRestClient` needs `ObjectMapper` with `SNAKE_CASE` + `JavaTimeModule` (same as github-collector)
- `Comment` uses `Instant` but API returns ISO-8601 strings — parsing layer handles conversion
- `PrReviewGate` (DD-8) is highest-risk custom code — test-first in Step 2.6
- `GenerateReport` must handle absent AI assessments via `ctx.get()` → `Optional.empty()`

---

## Per-Step Detail Files (Tier 2)

| File | Step | Topic |
|------|------|-------|
| `step-1.0-design-review.md` | 1.0 | API validation, 43 claims, DD-6 through DD-8 |
| `step-1.1-project-scaffolding.md` | 1.1 | pom.xml, dependency tree, Boot 4.0.3 |
| `step-1.2-quality-infrastructure.md` | 1.2 | ArchUnit rules, JaCoCo, spring-javaformat |
| `step-1.3-domain-models.md` | 1.3 | 12 records, factory method clash, defensive copying |
| `step-1.4-test-infrastructure.md` | 1.4 | Test fixtures, JSON payloads, github-collector reference |

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-04-08T17:30-04:00 | Seeded with three key learnings from Python system | Pre-implementation knowledge transfer |
| 2026-04-08T19:00-04:00 | Added API patterns from source exploration | Review feedback |
| 2026-04-08T19:30-04:00 | Added docs comparison findings | Docs comparison |
| 2026-04-08T20:00-04:00 | Added source validation findings (4 parallel agents) | Source code validation |
| 2026-04-08T22:45-04:00 | **Stage 1 consolidation** — compacted Steps 1.0–1.4 | Step 1.5 |
