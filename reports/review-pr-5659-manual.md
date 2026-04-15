# PR Review Report: #5659 — Allow custom StructuredOutputConverter(s) to participate in Native Structured Output

**Generated**: 2026-04-14 18:48:43 CEST
**Author**: filiphr | **Branch**: `support-structured-output-with-custom-converter` → `main` | **State**: open

---

## Summary

| Phase | Status | Details |
|-------|--------|--------|
| Rebase | PASS | Clean rebase on review/pr-5659 |
| Conflicts | PASS | Clean rebase, no conflicts |
| Build & Tests | PASS | All tests passed |
| AI Fix-Tests | PASS | Updated four test methods to use ChatOptions.Builder instead of StructuredOutputChatOptions object for .options() calls, matching the new API that expects builders rather than concrete options instances. |

**Overall Verdict**: PASS — All judges approved

---

## Phase 1: Deterministic Context Gathering

### PR Context

- **Title**: Allow custom StructuredOutputConverter(s) to participate in Native Structured Output
- **Files Changed**: 5
- **Lines**: +244 / -14
- **Labels**: none

### Rebase Result

- **Status**: PASS
- **Branch**: `review/pr-5659`

### Conflict Detection

- **Summary**: Clean rebase, no conflicts

### Build & Tests

- **Status**: PASS
- **Modules**: spring-ai-client-chat, spring-ai-model
- **Duration**: 32s

---

### AI Fix-Tests

- **Status**: PASS
- **Summary**: Updated four test methods to use ChatOptions.Builder instead of StructuredOutputChatOptions object for .options() calls, matching the new API that expects builders rather than concrete options instances.

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

- **Score**: NumericalScore[value=0.6149999999999999, min=0.0, max=1.0]
- **Reasoning**: Quality judge: 5/5 checks passed, composite score 0.61
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
- **Score**: 75%
- **Rationale**: The PR successfully generalizes native structured output support from BeanOutputConverter to any StructuredOutputConverter, which is a sensible API improvement. The implementation is clean with good test coverage for the happy path. However, there are edge case concerns: the default getJsonSchema() returns null, which could cause issues if native output is enabled without overriding this method; the change from immutable Map.copyOf() to mutable HashMap() lacks clear justification and could affect concurrent access patterns; and test coverage misses negative cases like null schema handling and backwards compatibility verification with existing BeanOutputConverter usage.
- **Findings**:
  - Missing guard or validation when getJsonSchema() returns null with native structured output enabled - could set null schema in context
  - Context mutability change (Map.copyOf to HashMap) alters immutability contract without clear rationale in PR description
  - Test coverage gaps: no negative test for null schema case, no explicit backwards compatibility test for BeanOutputConverter
  - Consider adding @Nullable annotation validation or warning log when schema is null to prevent silent failures

### backport

- **Status**: FAIL
- **Score**: 30%
- **Rationale**: This PR adds a new method (getJsonSchema) to the StructuredOutputConverter interface, which is a breaking API change. Any existing custom implementations of this interface in maintenance branches would fail to compile after backporting. Additionally, this is a feature enhancement that enables a new use case rather than a bug fix—the author describes using a workaround, indicating the original functionality was working as designed. The changes also modify core client behavior and advisor patterns, increasing integration risk.
- **Findings**:
  - Breaking change: adds getJsonSchema() method to StructuredOutputConverter interface
  - Feature enhancement, not a bug fix—enables custom converters for native structured output
  - Modifies core DefaultChatClient behavior by removing instanceof BeanOutputConverter checks
  - Changes ChatModelCallAdvisor context handling (HashMap vs Map.copyOf, introduces mutate pattern)
  - Extensive test additions (+220 lines) indicate significant functional scope
  - Would break compilation for any existing custom StructuredOutputConverter implementations
  - Builds on top of PR #5412, suggesting progressive feature development unsuitable for stable branches

---

## Files Changed

| File | Status | +/- |
|------|--------|-----|
| `spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/DefaultChatClient.java` | modified | +6 / -6 |
| `spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/advisor/ChatModelCallAdvisor.java` | modified | +3 / -6 |
| `spring-ai-client-chat/src/test/java/org/springframework/ai/chat/client/ChatClientNativeStructuredResponseTests.java` | modified | +222 / -2 |
| `spring-ai-model/src/main/java/org/springframework/ai/converter/BeanOutputConverter.java` | modified | +1 / -0 |
| `spring-ai-model/src/main/java/org/springframework/ai/converter/StructuredOutputConverter.java` | modified | +12 / -0 |

---

*Report generated by AgentWorks PR Review Pipeline*
