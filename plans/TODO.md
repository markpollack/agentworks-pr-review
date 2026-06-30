# Post-Workshop TODOs

## Sub-workflow context isolation in agent-workflow

**Problem:** When a `Workflow` is used as a `Step` inside another workflow, its internal
`updateContext()` writes are lost. The sub-workflow's executor creates a separate context
chain, and only the output value (not the final context) is returned to the parent.

**Impact on pr-review DSL workflow:**
1. Context-gathering steps had to be **flattened** into the main pipeline instead of
   composed as a sub-workflow. Otherwise PR_CONTEXT, REBASE_RESULT, etc. were invisible
   to the T0 gate.
2. The AI assessment phase had to be wrapped in a **composite step** (`AiAssessmentStep`)
   instead of using `Workflow.define().parallel(quality, backport).build()`. The DSL's
   `.parallel()` can't be used because the parallel sub-workflow's context writes are lost.
3. The `branch().otherwise(subWorkflow)` pattern doesn't propagate context from the
   sub-workflow back through the branch join.

**Proposed fix in agent-workflow:**
- `Workflow.execute()` should return both the output AND the final context, or
- `Workflow.updateContext()` should be overridden to replay the sub-workflow's context
  mutations, or
- The executor should detect when a StepNode wraps a Workflow and merge the sub-context
  back into the parent context after execution.

**Files affected if fixed:**
- `PrReviewDslWorkflow.java` — could re-introduce contextGathering sub-workflow and
  use `.parallel()` for AI assessment
- `AiAssessmentStep.java` — could be replaced with DSL `.parallel(quality, backport)`
- `FixAndRetestStep.java` — could potentially be replaced with DSL `.branch()` if
  `branchOnContext(Predicate<AgentContext>)` is also added

## branchOnContext(Predicate<AgentContext>) for agent-workflow

**Problem:** `branch(Predicate<Object>)` only receives the current output value, not the
`AgentContext`. Runtime decisions that depend on context values (e.g., accumulated state,
configuration flags resolved at runtime) require workarounds.

**Current workaround:** Capture configuration properties in lambda closures:
`branch(__ -> workshopProperties.skipAi())`

**Proposed enhancement:** Add `branchOnContext(Predicate<AgentContext>)` to the DSL
builder, backed by a context-aware GatewayNode variant.
