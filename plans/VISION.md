# Vision: AgentWorks PR Review Pipeline

> **Created**: 2026-04-08T18:00-04:00
> **Last updated**: 2026-04-08T19:00-04:00
> **Status**: Stable

## Problem Statement

The Spring AI project receives dozens of pull requests weekly. Reviewing them is time-consuming: each PR needs a rebase onto main, targeted test execution, version compatibility checking (Boot 3→4 migration patterns), code quality assessment, and backport candidacy evaluation. Today this is done manually or via a Python script pipeline (~20 scripts, 832KB) that works but is opaque — it shells out to Claude Code with no instrumentation, no structured logging, and no composable architecture. It cannot be taught in a workshop or extended by contributors.

The secondary audience is Spring conference workshop participants. They need a live, runnable PR review pipeline that demonstrates agentic workflow patterns (step composition, judge cascades, journal-based observability) against a real open-source repository. The Python system is too complex to teach and too fragile to run live.

## V2 — Generalization Beyond Spring AI (2026-06-30)

The reviewer has proven out on spring-ai (V1, complete). V2 generalizes it into the agentic-SDLC thesis: a **KB-consulting, control-theory-grounded reviewer**, applied first to **`markpollack/agent-experiment`** (Paul Bakker's PRs), then other repos one at a time. Three shifts: (1) it runs on the framework's **mapper-fed `JudgeGate`** + standard verdict trail — the upstream resolution of the DD-8 custom-gate workaround, not a per-consumer bridge; (2) its assessment **consults two knowledge bases** — `control-theory-kb` (the *why* / north star: what the harness measures and why) and `experiment-method-kb` (the data-discipline lens) — under a strict **anti-over-mapping** discipline (most PRs have no KB touchpoint, and saying so is a *good* review); (3) the reviewer itself becomes an **eval-agent**, measured against the experiment-method **3-PR gold standard** with over-mapping as the loss. Design: `DESIGN.md` Part 2. Execution: `ROADMAP-AGENT-EXPERIMENT.md` (the spring-ai workshop track stays in `ROADMAP.md`).

**V2 success criteria** (additional to V1's): the reviewer reviews an agent-experiment PR *framed in control theory*, flags genuine data-discipline touchpoints, **does not over-map** (reproduces the gold-standard calibration), and every run is journaled + `RunOptions`-cost-capped.

## Success Criteria

1. **End-to-end PR review** completes in under 10 minutes for a typical Spring AI PR (rebase, test, assess, report)
2. **Workshop-runnable**: a participant with Java 21, Git, and a GitHub token can `./mvnw spring-boot:run` and get a PR review report within the workshop session
3. **Observable execution**: every step, judge verdict, and AI call is recorded in AgentJournal — the diary is human-readable and suitable as a workshop teaching artifact
4. **Three-tier judge cascade** produces actionable verdicts: T0 (build), T1 (version patterns), T2 (LLM quality) — each tier adds value visible in the report
5. **Deterministic before probabilistic**: all deterministic checks (build, version patterns) complete and gate before any LLM spend

## Scope

### In Scope

- Fetch PR metadata, diff, comments, reviews, linked issues from GitHub REST API
- Rebase PR branch onto main
- Detect and classify merge conflicts (simple vs complex)
- Discover affected Maven modules and run targeted tests
- Three-tier judge cascade: BuildJudge (T0), VersionPatternJudge (T1), QualityJudge (T2)
- AI-powered code quality and backport assessment via AgentClient
- Markdown and HTML report generation from judge verdicts
- AgentJournal integration for full execution observability
- Pre-flight check command for workshop readiness validation
- Pre-recorded journal fallback when live execution fails
- "Point at your own repo" configuration override

### Out of Scope

- Automated conflict resolution (detection and classification only)
- Intelligent commit squashing (use simple `git rebase`)
- Compilation error auto-fixing (if tests pass, move on)
- Multiple AI assessor orchestration (Python had 5 separate assessors — replaced by 2 AI steps + 3-tier judges)
- Persistent storage or database (all state is in-memory per run)
- Web UI (CLI/Spring Boot runner only)
- Push/merge operations (read-only review pipeline)

## Unknowns and Research Questions

1. ~~Does `JudgeGate` support PASS/WARN/FAIL?~~ **Resolved**: `JudgeGate` is binary PASS/FAIL. `TieredGate` adds ESCALATE (our WARN equivalent). `CascadedJury` provides the fail-fast tiered cascade. See DESIGN.md DD-3 and DD-7.
2. What Journal event types exist for git operations? May need custom event types beyond LLMCallEvent/ToolCallEvent. (Resolve in Step 1.0)
3. Can AgentClient drive Claude Code for structured JSON output reliably, or do we need response parsing fallbacks? (Resolve in Step 3.3)
4. What's the actual GitHub API call count for a full PR review? Need to verify GITHUB_TOKEN rate limit math for 20+ concurrent workshop participants. (Resolve in Step 2.1)

## Assumptions

1. AgentWorks libraries (workflow-core, journal-core, agent-judge-core, agent-client-core) are stable at their current released versions and won't require API changes during implementation
2. Spring AI repository remains public and accessible without authentication for read operations
3. Workshop participants will have Java 21+, Git, and network access to GitHub API
4. Claude Code CLI is available on participant machines (or fallback journal suffices for the demo)
5. A single known-good PR (e.g., #5774) can serve as the default demonstration target

## Constraints

- **Technology**: Java 21+, Spring Boot 4.0.x, AgentWorks stack (workflow-flows 0.3.0, journal-core 0.9.0, agent-judge-core 0.9.1, agent-client-core 0.11.0). All deps via agentworks-bom 1.0.4.
- **No `gh` CLI**: Broadcom SAML SSO blocks OAuth tokens for spring-projects org. Must use direct REST API via Spring's RestClient.
- **Workshop**: Must be teachable in a 90-minute session. Pipeline complexity must be lower than the Python system, not higher.
- **Dependencies**: No SNAPSHOTs. All released versions only.

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-04-08T18:00-04:00 | Initial draft | Project creation |
| 2026-04-08T19:00-04:00 | Resolved JudgeGate question (#1), pinned Boot 3.5.x, corrected workflow-core→workflow-flows | Review feedback + source exploration |
| 2026-04-08T19:30-04:00 | Updated Spring Boot to 4.0.x (Boot 4 is GA, Spring AI 2.0 requires it) | User correction |
| 2026-06-30 | V2 — generalization beyond spring-ai (KB-consulting, control-theory-grounded, eval-agent); agent-experiment is the first target | Steward-first direction |
