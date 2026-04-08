# Vision: AgentWorks PR Review Pipeline

> **Created**: 2026-04-08T18:00-04:00
> **Last updated**: 2026-04-08T18:00-04:00
> **Status**: Stable

## Problem Statement

The Spring AI project receives dozens of pull requests weekly. Reviewing them is time-consuming: each PR needs a rebase onto main, targeted test execution, version compatibility checking (Boot 3→4 migration patterns), code quality assessment, and backport candidacy evaluation. Today this is done manually or via a Python script pipeline (~20 scripts, 832KB) that works but is opaque — it shells out to Claude Code with no instrumentation, no structured logging, and no composable architecture. It cannot be taught in a workshop or extended by contributors.

The secondary audience is Spring conference workshop participants. They need a live, runnable PR review pipeline that demonstrates agentic workflow patterns (step composition, judge cascades, journal-based observability) against a real open-source repository. The Python system is too complex to teach and too fragile to run live.

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

1. Does `workflow-core` 0.3.0's `JudgeGate` support the PASS/WARN/FAIL three-state verdict model, or only binary PASS/FAIL?
2. What Journal event types exist for git operations? May need custom event types beyond LLMCallEvent/ToolCallEvent.
3. Can AgentClient drive Claude Code for structured JSON output reliably, or do we need response parsing fallbacks?
4. What's the actual GitHub API call count for a full PR review? Need to verify GITHUB_TOKEN rate limit math for 20+ concurrent workshop participants.

## Assumptions

1. AgentWorks libraries (workflow-core, journal-core, agent-judge-core, agent-client-core) are stable at their current released versions and won't require API changes during implementation
2. Spring AI repository remains public and accessible without authentication for read operations
3. Workshop participants will have Java 21+, Git, and network access to GitHub API
4. Claude Code CLI is available on participant machines (or fallback journal suffices for the demo)
5. A single known-good PR (e.g., #5774) can serve as the default demonstration target

## Constraints

- **Technology**: Java 21+, Spring Boot 3.5+, AgentWorks stack (workflow-core 0.3.0, journal-core 0.9.0, agent-judge-core 0.9.1, agent-client-core 0.11.0). All deps via agentworks-bom 1.0.4.
- **No `gh` CLI**: Broadcom SAML SSO blocks OAuth tokens for spring-projects org. Must use direct REST API via Spring's RestClient.
- **Workshop**: Must be teachable in a 90-minute session. Pipeline complexity must be lower than the Python system, not higher.
- **Dependencies**: No SNAPSHOTs. All released versions only.

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-04-08T18:00-04:00 | Initial draft | Project creation |
