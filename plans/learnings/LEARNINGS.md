# Learnings: AgentWorks PR Review Pipeline

> **Last compacted**: 2026-04-08T17:30-04:00
> **Covers through**: Pre-implementation (seeded from Python system experience)

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

(To be populated during implementation)

## Deviations from Design

| Design says | Implementation does | Why |
|-------------|-------------------|-----|

## Common Pitfalls

1. **Don't use `gh` CLI** — Broadcom's SAML SSO enforcement blocks `gh` CLI OAuth tokens for spring-projects org. Use direct REST API calls (RestClient in Java, urllib in Python). This affects any GitHub operation on repos in SAML-protected orgs.

---

## Per-Step Detail Files (Tier 2)

| File | Step | Topic |
|------|------|-------|

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-04-08T17:30-04:00 | Seeded with three key learnings from Python system | Pre-implementation knowledge transfer |
