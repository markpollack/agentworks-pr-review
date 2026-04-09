# Learnings: AgentWorks PR Review Pipeline

> **Last compacted**: 2026-04-08T19:00-04:00
> **Covers through**: Pre-implementation (seeded from Python system + API exploration)

This is the **Tier 1 compacted summary**. Read this first for the current state of project knowledge. For details on specific steps, see the per-step files (Tier 2).

---

## Key Discoveries

Findings from the Python system that must inform the Java design:

1. **AgentJournal is non-negotiable from step 1** — Claude Code subprocess calls without instrumentation produce no diagnosable output. The Python system called Claude Code as raw subprocesses with no structured logging, making failures opaque. Journal integration must be wired into every step from the start, not retrofitted later.
   - *Source*: Python `claude_code_wrapper.py` experience
   - *Impact*: Every Step implementation must accept and use Journal from day one. No "add logging later" shortcuts.

2. **Version pattern check is the highest-value deterministic judge** — The Boot 3→4 migration patterns (`@WebMvcTest`/`@DataJpaTest` package moves, `MockMvc` → `MockMvcTester`, `javax` → `jakarta`) catch real issues that LLM assessment misses or over-generalizes. Implement `VersionPatternJudge` (T1) early in Stage 3, not as the last judge.
   - *Source*: Python `ai_risk_assessor.py` — version checks buried inside LLM prompts, unreliable
   - *Impact*: T1 judge runs before any LLM spend. Deterministic always before probabilistic.

3. **GitHub API rate limits hit fast with multiple participants** — Unauthenticated access is 60 req/hr. A single PR review uses ~10-15 API calls. With 10 workshop participants, that's 100-150 requests in minutes. `GITHUB_TOKEN` setup must be in the pre-flight check as a hard requirement, not a README footnote.
   - *Source*: Python `github_rest_client.py` — hit rate limits during batch PR runs
   - *Impact*: Pre-flight check must verify GITHUB_TOKEN presence and remaining rate limit headroom. Workshop instructions must make token setup step 1, not optional.

## Patterns Established

- **Step<I,O> is in `workflow-flows`, not `workflow-core`**: The high-level DSL (`Step`, `Workflow`, `AgentContext`, `ContextKey`, `JudgeGate`, `TieredGate`) lives in `io.github.markpollack:workflow-flows:0.3.0`. `workflow-core` contains the lower-level loop patterns (`LoopPattern`, `TurnLimitedLoop`, `GraphNode`).
- **AgentContext is the cross-cutting data bus**: Immutable accumulator with `ContextKey<T>`. Steps publish via `updateContext()`, executor auto-publishes step outputs under `Steps.outputOf(stepName)`. Later steps read any prior step's output — solves the "RunTestsStep needs PrContext but input is ConflictReport" problem.
- **Judge.judge(JudgmentContext) → Judgment**: Not `evaluate()`, not `Verdict`. `JudgmentStatus` has PASS/FAIL/ABSTAIN/ERROR — no WARN. For warning semantics, use `NumericalScore` with `TieredGate` (PASS/ESCALATE/FAIL).
- **CascadedJury for the three-tier cascade**: `CascadedJury.builder().tier("name", jury, TierPolicy.REJECT_ON_ANY_FAIL)` — purpose-built for fail-fast tiered evaluation. No custom cascade needed.
- **AgentClient → AgentModel → ClaudeAgentModel → claude-code-sdk**: Four-layer dependency chain. `agent-client-core` is the facade, `agent-model` is the SPI, `agent-claude` is the Claude CLI bridge (runtime dep), `claude-code-sdk` is the pure-Java CLI driver.
- **CascadedJury preserves per-tier verdicts**: `verdict.subVerdicts()` returns each tier's full `Verdict` (with `individual` and `individualByName`). Report can show per-tier breakdown — the "AI said LGTM, judge said wait" story is supported.

## Deviations from Design

| Design says | Implementation does | Why |
|-------------|-------------------|-----|

## Docs Comparison Findings

Validated against `~/projects/docs/docs/agent-workflow/` and `~/projects/workflow-dsl-examples/`:

1. **JudgeGate does NOT bridge AgentContext → JudgmentContext** — `JudgeGate.evaluate()` receives `AgentContext ctx` but only passes `output.toString()` to `JudgmentContext.agentOutput`. Judges that need `PrContext` require a custom gate adapter (`PrReviewGate`) that reads from context and populates `JudgmentContext.metadata()`. Documented as DD-8.
2. **Parameterization has 3 levels — use sparingly** — Level 1: input chaining (default). Level 2: `Steps.outputOf("stepName")` for non-adjacent data. Level 3: `updateContext()` with `ContextKey` for side-channel metadata. Our `ContextKeys.PR_CONTEXT` is Level 3 — appropriate since multiple downstream consumers need it independently.
3. **AgentClientStep.of(client, template)** is a built-in step type for AI calls — evaluate whether it's sufficient for our AI steps or if custom steps give better control over prompt construction.
4. **WorkflowGraphAssert.assertTypeCompatible(graph)** — catches I→O type mismatches at test time. Add to workflow composition tests.
5. **RunOptions.maxCost(5.0)** — built-in cost and duration controls for workshop safety.
6. **Reflector pattern** — `.withReflector(step)` on gate builder extracts failure reasons from verdict and writes to `JUDGE_REFLECTION` context key. Useful for retry loops.

## Source Validation Findings

Full API validation against source code (4 parallel agents):

1. **workflow-flows**: 12/12 claims validated. Step, AgentContext, ContextKey, JudgeGate, TieredGate, GateDecision, Gate, Workflow DSL, WorkflowGraphAssert, RunOptions, Step.named() — all match.
2. **agent-judge-core**: 12/12 claims validated. Judge, Judgment, JudgmentStatus, JudgmentContext, Score (sealed), CascadedJury, TierPolicy, Jury, Verdict, SimpleJury, DeterministicJudge, Check — all match.
3. **agent-client**: 6/7 claims validated. AgentClient package is `org.springaicommunity.agents.client` (not `.agents`). `AgentClientStep` does NOT exist in agent-client — it's in workflow-flows.
4. **journal-core**: Run tracking API validated. Git events have 4 subtypes (GitPatchEvent, GitCommitEvent, GitBranchEvent, GitPullRequestEvent). Default storage is `InMemoryStorage`, not `JsonFileStorage`. `workflow-journal` module and `JournalContextPolicy` do NOT exist.

Key corrections applied:
- **Two `AgentClient` interfaces exist**: workflow-flows has `String execute(String, AgentContext)` (simple); agent-client-core has the full fluent builder API. `AgentClientStep.of()` uses the workflow-flows one.
- **Journal storage**: Must explicitly configure `JsonFileStorage` for file persistence; `InMemoryStorage` is the default.

## Common Pitfalls

1. **Don't use `gh` CLI** — Broadcom's SAML SSO enforcement blocks `gh` CLI OAuth tokens for spring-projects org. Use direct REST API calls (RestClient in Java, urllib in Python). This affects any GitHub operation on repos in SAML-protected orgs.
2. **JudgeGate metadata gap** — Built-in `JudgeGate` only passes `output.toString()` to judges. Any judge needing structured data from earlier steps must get it through a custom gate that enriches `JudgmentContext.metadata()`.
3. **Two AgentClient interfaces** — Don't confuse workflow-flows' `AgentClient` (`String execute(String, AgentContext)`) with agent-client-core's `AgentClient` (full fluent builder). They are separate. `AgentClientStep.of()` uses the workflow-flows one.

---

## Per-Step Detail Files (Tier 2)

| File | Step | Topic |
|------|------|-------|

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-04-08T17:30-04:00 | Seeded with three key learnings from Python system | Pre-implementation knowledge transfer |
| 2026-04-08T19:00-04:00 | Added API patterns from source exploration (workflow-flows, agent-judge, agent-client) | Review feedback |
| 2026-04-08T19:30-04:00 | Added docs comparison findings (JudgeGate gap, parameterization levels, AgentClientStep, WorkflowGraphAssert) | Docs comparison |
| 2026-04-08T20:00-04:00 | Added source validation findings (4 parallel agents, all APIs verified, corrections applied) | Source code validation |
