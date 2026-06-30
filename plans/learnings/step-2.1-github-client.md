# Step 2.1 Learnings: GitHub REST Client

## What Was Done
- `GitHubRestClient.java` — Spring RestClient-based GitHub API client
- `GitHubProperties.java` — `@ConfigurationProperties("github")` for base URL, repo, token
- `WorkshopProperties.java` — `@ConfigurationProperties("workshop")` for default PR, skip-ai, journal-dir
- `@ConfigurationPropertiesScan` on `PrReviewApplication`
- WireMock tests: 12 tests covering PR metadata, files, comments, reviews, rate limits, linked issue parsing
- WireMock 3.13.1 added to pom.xml

## Key Discoveries

### Boot 4 uses Jackson 3.x (`tools.jackson.databind`)
Spring Boot 4 moved from Jackson 2.x (`com.fasterxml.jackson.databind`) to Jackson 3.x (`tools.jackson.databind`). RestClient's message converters use Jackson 3.x, so `body(JsonNode.class)` must reference `tools.jackson.databind.JsonNode`, not `com.fasterxml.jackson.databind.JsonNode`. The Jackson 2.x class is still on the classpath (from transitive deps) so it compiles fine but fails at runtime with "Cannot construct instance of com.fasterxml.jackson.databind.JsonNode".

**Pattern**: Always use `tools.jackson.databind.*` in Boot 4 projects.

### RestClient URI template with positional variables
RestClient supports `{name}` URI templates with positional Object... args. We prepend owner/repo to every call via `expandUriVariables()` helper. Templates like `/repos/{owner}/{repo}/pulls/{number}` work cleanly.

### Linked issue parsing
GitHub API doesn't directly expose "linked issues" on the PR endpoint. We parse "Fixes #NNN" / "Closes #NNN" / "Resolves #NNN" patterns from the PR body via regex. URL-form links (`Fixes https://github.com/.../issues/889`) are NOT matched — this is intentional for simplicity.

### RateLimitInfo for pre-flight check
Dedicated `/rate_limit` endpoint returns structured rate limit data. `RateLimitInfo.hasHeadroom(int needed)` lets the pre-flight check verify enough capacity for a full pipeline run (~15 API calls per PR).

## Design Decisions
- **No retry/pacing decorator** — unlike github-collector's `RetryingGitHubClient`, we keep it simple. Workshop runs are single PR, not batch. If rate limited, the error message is clear enough.
- **Linked issues are stub records** — `Issue(number, "", List.of())` — we only extract the number from the PR body. Full issue fetch could be added but isn't needed for the review pipeline.

## Pitfalls for Next Steps
- `tools.jackson.databind` vs `com.fasterxml.jackson.databind` — any Jackson usage must use the 3.x package
- WireMock tests create a new `GitHubRestClient` per test (not Spring-managed) — works for unit testing but won't test Spring wiring. Integration test would need `@SpringBootTest` + WireMock server.
