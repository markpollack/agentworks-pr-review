# Step 3.6: Stage 3 Summary — AI Assessment Pipeline

**Completed**: 2026-04-09

## What Stage 3 Delivered

Complete three-tier judge cascade and AI assessment steps:

| Component | Type | Role |
|-----------|------|------|
| `VersionPatternJudge` | Judge (T1 deterministic) | 5 Boot 3→4 patterns, scans added lines only |
| `AssessCodeQualityStep` | Step<PrContext, AssessmentResult> | AI quality review via AgentClient |
| `AssessBackportStep` | Step<PrContext, AssessmentResult> | AI backport candidacy via AgentClient |
| `QualityJudge` | Judge (T2 LLM) | Cross-checks assessment consistency |
| `AssessmentParser` | Utility | Regex-based JSON response parser |
| `PromptHelper` | Utility | Shared prompt template rendering |
| Prompt templates | Resources | code-quality-assessment.md, backport-assessment.md |

## Key Design Decisions

1. **VersionPatternJudge scans added lines only** — `^\\+` prefix in regex ensures removed lines (deletions fixing old patterns) don't trigger false positives.

2. **Regex-based JSON parsing** — `AssessmentParser` uses regex instead of Jackson to parse AI responses. The response format is constrained (score, status, rationale, findings), and this avoids coupling the parsing path to Jackson version concerns. Handles malformed responses gracefully.

3. **QualityJudge uses NumericalScore** — Weighted composite: 70% quality + 30% backport. Unlike T0/T1's BooleanScore, T2 produces a gradient suitable for TieredGate thresholds.

4. **AgentClient from agent-client-core** — Uses `org.springaicommunity.agents.client.AgentClient` (fluent API), not workflow-flows' simple one. Constructor-injected for testability with Mockito mocks.

5. **AgentClientResponse construction for tests** — `new AgentResponse(List.of(new AgentGeneration(text)))` → `new AgentClientResponse(response)`. No `AgentResult` class exists (was hallucinated from training data).

## Agent-Client API Notes

- `AgentClient.run(String goalText)` → `AgentClientResponse` — convenience method
- `AgentClientResponse.getResult()` → String — extracts first generation's text
- `AgentGeneration(String text)` — single-arg constructor, null metadata OK
- `AgentResponse(List<AgentGeneration>)` — takes list of generations

## Test Coverage (Stage 3)

- VersionPatternJudge: 10 tests (clean, javax, jackson, mockbean, mockmvc, websecurity, multi-file, singular)
- AssessmentParser: 6 tests (valid JSON, fail status, empty findings, null, blank, missing fields)
- AssessCodeQualityStep: 4 tests (success, error, context publish, prompt rendering)
- AssessBackportStep: 4 tests (success, error, context publish, prompt rendering)
- QualityJudge: 7 tests (both good, weighted score, missing quality/backport, error state, contradictory, both fail)

Total new tests: 31
