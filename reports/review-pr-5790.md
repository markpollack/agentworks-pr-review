# PR Review Report: #5790 — Fix silent null-arg tool dispatch causing runaway tool-call loops

**Generated**: 2026-04-15 10:47:51 CEST
**Author**: laran | **Branch**: `fix-silent-tool-arg-loop` → `main` | **State**: open

---

## Summary

| Phase | Status | Details |
|-------|--------|--------|
| Rebase | PASS | Clean rebase on review/pr-5790 |
| Conflicts | PASS | Clean rebase, no conflicts |
| Build & Tests | PASS | All tests passed |

**Overall Verdict**: PASS — All judges approved

---

## Phase 1: Deterministic Context Gathering

### PR Context

- **Title**: Fix silent null-arg tool dispatch causing runaway tool-call loops
- **Files Changed**: 9
- **Lines**: +393 / -98
- **Labels**: none

### Rebase Result

- **Status**: PASS
- **Branch**: `review/pr-5790`

### Conflict Detection

- **Summary**: Clean rebase, no conflicts

### Build & Tests

- **Status**: PASS
- **Modules**: mcp/common, spring-ai-model
- **Duration**: 29s

---

## Phase 2: Judge Cascade

### PASS

- **Score**: BooleanScore[value=true]
- **Reasoning**: Build judge: 4/4 checks passed
- **Checks**:
  - PASS: rebase-clean — 
  - PASS: no-complex-conflicts — 
  - PASS: build-executed — 
  - PASS: tests-passed — 

### PASS

- **Score**: BooleanScore[value=true]
- **Reasoning**: Version pattern judge: no migration anti-patterns detected

### PASS

- **Score**: NumericalScore[value=0.82, min=0.0, max=1.0]
- **Reasoning**: Quality judge: 5/5 checks passed, composite score 0.82
- **Checks**:
  - PASS: quality-present — 
  - PASS: backport-present — 
  - PASS: quality-no-error — 
  - PASS: backport-no-error — 
  - PASS: consistency — 

---

## Phase 3: AI Assessments

### code-quality

- **Status**: PASS
- **Score**: 85%
- **Rationale**: This PR correctly addresses a critical bug causing runaway tool-call loops by replacing silent failure with explicit ToolExecutionException throws. The implementation is clean, well-documented with clear rationale comments, and thoroughly tested across all affected components (MCP callbacks, DefaultToolCallingManager, MethodToolCallback). Tests verify both the error path and that underlying services aren't invoked on malformed input. Exception messages are descriptive and actionable. The breaking change is justified - old behavior caused severe production issues. Code style is consistent with Spring conventions.
- **Findings**:
  - Exception handling correctly prevents runaway loops by surfacing errors to the model instead of silent '{}' substitution
  - Test coverage is comprehensive with explicit loop-safety documentation and verification that mocked services are never called
  - Comments clearly explain the 'why' behind the change, referencing the previous buggy behavior for maintainability
  - Required vs optional parameter handling in MethodToolCallback properly distinguishes @ToolParam(required=false) cases
  - Consider adding migration notes in CHANGELOG since this changes tool callback contract from silent to fail-fast

### backport

- **Status**: PASS
- **Score**: 75%
- **Rationale**: This is a focused fix for a critical bug that can cause runaway tool-call loops consuming millions of tokens. The change is well-scoped to tool callback error handling in four classes and introduces no new dependencies or API surface changes. While it does change behavior by throwing exceptions instead of silently substituting '{}' for null arguments, the old behavior was clearly defective and could cause severe resource exhaustion. The comprehensive test coverage and alignment with five related issues demonstrate this is a mature fix addressing a known production problem. The primary backport risk is existing code that inadvertently relied on the silent-failure behavior, but such code was already fundamentally broken.
- **Findings**:
  - Fixes critical bug causing multi-million-token runaway loops (references 5 related issues: #5754, #3333, #2383, #4464, #4617)
  - Focused scope: changes only tool callback error handling in 4 classes (DefaultToolCallingManager, MethodToolCallback, AsyncMcpToolCallback, SyncMcpToolCallback)
  - No new dependencies or public API additions; uses existing ToolExecutionException type
  - Behavior change: now throws explicit exceptions instead of silently using '{}' for null/empty tool arguments - breaking but justified
  - Well-tested: 3 test classes updated with specific coverage for new exception paths, including loop-safety verification
  - Medium backport risk: code relying on silent-failure behavior will now see exceptions, but that code was already producing incorrect results

---

## Files Changed

| File | Status | +/- |
|------|--------|-----|
| `mcp/common/src/main/java/org/springframework/ai/mcp/AsyncMcpToolCallback.java` | modified | +11 / -3 |
| `mcp/common/src/main/java/org/springframework/ai/mcp/SyncMcpToolCallback.java` | modified | +11 / -3 |
| `mcp/common/src/test/java/org/springframework/ai/mcp/AsyncMcpToolCallbackTest.java` | modified | +27 / -32 |
| `mcp/common/src/test/java/org/springframework/ai/mcp/SyncMcpToolCallbackTests.java` | modified | +28 / -13 |
| `spring-ai-model/src/main/java/org/springframework/ai/model/tool/DefaultToolCallingManager.java` | modified | +25 / -11 |
| `spring-ai-model/src/main/java/org/springframework/ai/tool/method/MethodToolCallback.java` | modified | +25 / -0 |
| `spring-ai-model/src/test/java/org/springframework/ai/model/tool/DefaultToolCallingManagerTest.java` | modified | +144 / -30 |
| `spring-ai-model/src/test/java/org/springframework/ai/model/tool/DefaultToolCallingManagerTests.java` | modified | +11 / -6 |
| `spring-ai-model/src/test/java/org/springframework/ai/tool/method/MethodToolCallbackExceptionHandlingTest.java` | modified | +111 / -0 |

---

*Report generated by AgentWorks PR Review Pipeline*
