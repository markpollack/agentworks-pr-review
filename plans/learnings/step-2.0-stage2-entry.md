# Step 2.0: Stage 2 Entry

> **Completed**: 2026-04-08T23:00-04:00

## Review

Stage 1 learnings loaded. No blocking issues. Key decisions for Stage 2:

- **Write GitHubRestClient fresh** using Spring RestClient (not copy from github-collector). Simpler, more readable for workshop.
- **github-collector patterns inform but don't dictate** — we flatten Author to String, skip ReviewComment, use simpler PrContext.
- **Rate limit header extraction** is the one pattern worth preserving from github-collector — critical for 20+ concurrent workshop participants.
- **ObjectMapper**: SNAKE_CASE + JavaTimeModule for GitHub API JSON parsing.
- **Fallback journal** deferred to Stage 4 (confirmed in Step 1.4).
