# Step 1.0: Codebase Review and Design Validation

> **Completed**: 2026-04-08T20:00-04:00

## What was done

Validated all API claims in DESIGN.md against actual source code across 4 projects (43 claims, 4 parallel agents). Created DD-6 through DD-8. Resolved all 6 review issues from first design pass.

## Key findings

1. **Step<I,O> is in workflow-flows, not workflow-core** — workflow-core has lower-level loop patterns
2. **JudgmentStatus has no WARN** — PASS/FAIL/ABSTAIN/ERROR only. Use NumericalScore + TieredGate (ESCALATE) for warning semantics
3. **JudgeGate does NOT bridge AgentContext → JudgmentContext** — need custom PrReviewGate (DD-8) to populate JudgmentContext.metadata() from AgentContext
4. **Two AgentClient interfaces** — workflow-flows has simple `String execute(String, AgentContext)`; agent-client-core has full fluent builder API. AgentClientStep.of() uses the workflow-flows one.
5. **CascadedJury preserves per-tier verdicts** in `subVerdicts()` with `individualByName` — supports the workshop teaching story
6. **Journal default storage is InMemoryStorage**, not JsonFileStorage — must explicitly configure for persistence
7. **workflow-journal module doesn't exist** — removed from design
8. **AgentContext is the cross-cutting data bus** — immutable accumulator with ContextKey<T>. Steps publish via updateContext(), executor auto-publishes under Steps.outputOf(stepName)

## Corrections applied

- Boot 3.5.x → Boot 4.0.x (Boot 4 is GA, Spring AI 2.0 requires it)
- Added DD-6 (AgentContext data bus), DD-7 (no WARN), DD-8 (PrReviewGate)
- Resolved Open Question #5 (JudgeGate metadata gap)
- Added WorkflowGraphAssert and RunOptions to test/workshop strategy
- Corrected journal storage defaults
- Clarified dual AgentClient interface situation

## Impact on next steps

- Step 1.1 pom.xml must include workflow-flows (not workflow-core alone), agent-claude as runtime dep
- PrReviewGate should be tested first in Step 2.6
- GenerateReport must handle absent AI assessments (Optional.empty() on gate fail path)
- Journal needs explicit JsonFileStorage configuration for workshop file output
