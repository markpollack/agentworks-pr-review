# Step 1.4 Learnings: Test Infrastructure

## What Was Done
- `TestPrContexts.java` — 3 factory methods: `pr5774()` (real PR, minimal), `prWithReviews()` (full fields), `minimal()` (edge case)
- `TestAssessments.java` — factories for all judge tiers (buildPass/Fail, versionPatternPass/Fail, qualityPass/Fail), Judgment wrappers, BuildResult, ConflictReport, and complete ReviewReport fixtures
- JSON fixtures in `src/test/resources/fixtures/` — PR metadata, files, comments, reviews for both PR 5774 (empty reviews) and a synthetic PR with reviews (populated reviews/comments)

## Key Discoveries

### github-collector as reference
`/home/mark/tuvium/projects/github-collector` has production-quality GitHub REST API DTOs. Key patterns:
- ObjectMapper uses `SNAKE_CASE` naming strategy (`createdAt` ↔ `created_at`)
- `Author` is a separate record (`login`, nullable `name`)
- Issue comments (`/issues/{n}/comments`) vs review comments (`/pulls/{n}/comments`) are different endpoints
- Reviews can contain nested review comments grouped by `pullRequestReviewId`

Our `PrContext` model is simpler (flattened author to `String`, no `ReviewComment` type) — this is intentional for workshop teachability but means our `GitHubRestClient` will need to flatten the API responses.

### JSON fixture format
Fixtures use raw GitHub REST API format (snake_case, nested `user.login`, `base.ref`) — NOT our domain model format. This is correct: they'll be used to test the `GitHubRestClient` parsing layer in Stage 2.

## Deferred
- **Fallback journal** (`fallback/pr-5774-journal.jsonl`) deferred to Stage 4 — can't validate format until pipeline produces real events and JsonFileStorage is wired.

## Pitfalls for Next Steps
- Our `Comment` record uses `Instant` but GitHub API returns ISO-8601 strings — the parsing layer needs `JavaTimeModule` with SNAKE_CASE (same as github-collector's `ObjectMapperFactory`)
- Two fixture sets: `pr-5774-*` (real, empty reviews) and `pr-with-reviews-*` (synthetic, populated) — use both to test happy path and review-present path
