# AgentWorks PR Review Pipeline

## Project
Java rewrite of the Python PR review/merge pipeline using the AgentWorks stack.
Workshop-teachable PR review pipeline for Spring conferences.

## Tracking
- `plans/ROADMAP.md` is the source of truth for implementation progress
- Execute steps individually, capture learnings after each step
- Read prior step learnings before starting the next step

## Build
```bash
./mvnw compile    # compile
./mvnw test       # unit tests
./mvnw verify     # full build with quality checks
```

## Key AgentWorks Dependencies (all released, no SNAPSHOTs)
- `agentworks-bom` 1.0.4 (`io.github.markpollack`)
- `workflow-core` 0.3.0 (`io.github.markpollack`) — Step<I,O>, Workflow DSL
- `journal-core` 0.9.0 (`io.github.markpollack`) — Run tracking, events
- `agent-judge-core` 0.9.1 (`org.springaicommunity`) — Judge framework
- `agent-client-core` 0.11.0 (`org.springaicommunity.agents`) — AgentClient for Claude Code

## AgentWorks Source
- Local source: `~/projects/agentworks/`
- Prefer reading source over decompiling from ~/.m2

## Architecture
Three-phase pipeline:
1. **Deterministic Context Gathering** — GitHub API, git rebase, conflict detection, tests
2. **AI Assessment** — Code quality + backport assessment via AgentClient
3. **Report Generation** — Markdown/HTML from judge verdicts

Judge cascade: **T0 (BuildJudge)** → **T1 (VersionPatternJudge)** → AI steps → **T2 (QualityJudge)**
- T0/T1 are deterministic (no AI)
- T2 only fires if T0 and T1 pass or warn

## Python Reference
- Original Python pipeline: `~/projects/spring-ai-project-mgmt/pr-review/`
- Portable copy: `/tmp/prmerge/`
