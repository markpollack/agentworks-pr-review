# AgentWorks PR Review Pipeline

A workshop-teachable PR review pipeline for [spring-projects/spring-ai](https://github.com/spring-projects/spring-ai) built with the AgentWorks stack.

## Prerequisites

### 1. Java 21+

```bash
java -version   # must be 21 or later
```

### 2. Claude Code

The AI assessment steps delegate to the `claude` CLI. See the [Claude Code installation docs](https://docs.anthropic.com/en/docs/claude-code/getting-started) to get set up.

### 3. Local clone of spring-ai

The pipeline rebases PRs and runs tests locally, so it needs a clone of the target repository:

```bash
git clone https://github.com/spring-projects/spring-ai.git ../spring-ai
```

The default location is `../spring-ai` (sibling directory). Override with:

```bash
# Environment variable
export SPRING_AI_REPO_DIR=/path/to/spring-ai

# Or command-line property
--workshop.repo-dir=/path/to/spring-ai
```

### 4. GitHub token (recommended)

Without a token the GitHub API rate limit is 60 requests/hour. Set a personal access token for 5,000 req/hr:

```bash
export GITHUB_TOKEN=ghp_...
```

## Quick Start

```bash
# Pre-flight check — validates all prerequisites
./mvnw spring-boot:run -Dspring-boot.run.arguments="--check"

# Review a specific PR (default: 5774)
./mvnw spring-boot:run -Dspring-boot.run.arguments="5774"
```

When complete, open the generated report:

```bash
open reports/review-pr-5774.html     # macOS
xdg-open reports/review-pr-5774.html # Linux
```

## Output

- **Report**: `reports/review-pr-{N}.html` and `reports/review-pr-{N}.md`
- **Journal diary**: `journal/experiments/pr-review/runs/{uuid}/events.jsonl`

The journal diary is the "let me show you what the agent did" artifact — a machine-readable trace of every step, judge verdict, state transition, and LLM call.

## Configuration

All properties can be set in `application.yml`, as environment variables, or as command-line arguments:

| Property | Default | Description |
|----------|---------|-------------|
| `workshop.default-pr` | 5774 | PR number when none specified on command line |
| `workshop.fix-tests` | false | Enable AI fix-tests step when tests fail |
| `workshop.use-dsl` | true | Use DSL workflow (false = manual orchestrator) |
| `workshop.journal-dir` | `./journal` | Directory for journal output |
| `workshop.repo-dir` | `../spring-ai` | Local clone of the target repository |
| `github.repo` | `spring-projects/spring-ai` | GitHub owner/repo |
| `github.base-url` | `https://api.github.com` | GitHub API base URL |
| `github.token` | (none) | GitHub personal access token |

## Pipeline Phases

1. **Phase 1: Deterministic Context Gathering** — fetch PR metadata, rebase, detect conflicts, run tests
2. **T0 Gate: Build Judge** — deterministic pass/fail on rebase, conflicts, and test results
3. **T1 Gate: Version Pattern Judge** — deterministic scan for Boot 3-to-4 migration anti-patterns (FAIL sets verdict but does not block AI)
4. **Phase 2: AI Assessment** — Claude-powered code quality and backport assessment (skipped only if T0 fails)
5. **T2 Gate: Quality Judge** — LLM meta-judge cross-checking AI assessments
6. **Phase 3: Report Generation** — markdown report summarizing all findings

## Workflow Modes

The pipeline ships with two workflow implementations:

- **DSL workflow** (default) — composed using the `Workflow.define()` DSL from agent-workflow
- **Manual workflow** — explicit step-by-step orchestration for maximum readability

Switch between them:

```bash
# DSL (default)
./mvnw spring-boot:run -Dspring-boot.run.arguments="5774"

# Manual orchestrator
./mvnw spring-boot:run -Dspring-boot.run.arguments="5774 --workshop.use-dsl=false"
```

Both produce identical reports. Previous manual reports are preserved as `reports/review-pr-*-manual.*` for comparison.

## Build

```bash
./mvnw verify          # full build with tests and quality checks
./mvnw test            # tests only
```
