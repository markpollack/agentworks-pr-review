# Step 4.0: Stage 4 Entry — Review and Context Load

**Completed**: 2026-04-09

## Pipeline Data Flow Review

Full pipeline: FetchPrContext (Integer → PrContext) → RebaseStep (PrContext → RebaseResult) → ConflictDetection (RebaseResult → ConflictReport) → RunTests (ConflictReport → BuildResult) → [T0: BuildJudge] → [T1: VersionPatternJudge] → AssessCodeQuality + AssessBackport (PrContext → AssessmentResult) → [T2: QualityJudge] → GenerateReport → Path

### Type Chain Issues

The Workflow DSL chains `step_n.output → step_(n+1).input`. After RunTestsStep (→ BuildResult), the AI steps need PrContext as input. This creates a type break. Resolution options:

1. **Bridge steps** — lambda steps that read from AgentContext: `(ctx, buildResult) → ctx.require(PR_CONTEXT)`
2. **Manual orchestrator** — explicit step calls with context management (more workshop-readable)
3. **Modify AI steps** — read PrContext from context instead of input parameter

### DD-8: Custom Gate for Judges

JudgeGate only passes `output.toString()` to JudgmentContext. Our judges need structured domain objects in metadata. Solutions:

- Create `PrReviewGate` implementing `Gate<O>` that reads from AgentContext and constructs JudgmentContext with proper metadata keys
- One gate per tier (T0, T1, T2) since each reads different metadata

### Integration Approach

Use **manual orchestrator pattern** for PrReviewWorkflow:
- More readable for workshop participants
- Explicit step calls with AgentContext threading
- Custom judge evaluation at gate points
- No DSL type-bridging gymnastics
- Still demonstrates Steps, Judges, Context, Journal

The Workflow DSL can be shown in slides as "what this compiles to" — the manual code shows participants exactly what happens.

## ContextKey Dependencies (Full Map)

| Published By | ContextKey | Read By |
|-------------|-----------|---------|
| FetchPrContextStep | PR_CONTEXT | RunTestsStep, VersionPatternJudge, AI steps |
| AssessCodeQualityStep | QUALITY_ASSESSMENT | QualityJudge |
| AssessBackportStep | BACKPORT_ASSESSMENT | QualityJudge |
| All steps | Steps.outputOf(name) | Auto-stored by executor |

## Judge Metadata Keys

| Judge | Metadata Key | Source |
|-------|-------------|--------|
| BuildJudge | REBASE_RESULT | RebaseStep output |
| BuildJudge | CONFLICT_REPORT | ConflictDetectionStep output |
| BuildJudge | BUILD_RESULT | RunTestsStep output |
| VersionPatternJudge | PR_CONTEXT | FetchPrContextStep output |
| QualityJudge | QUALITY_ASSESSMENT | AssessCodeQualityStep output |
| QualityJudge | BACKPORT_ASSESSMENT | AssessBackportStep output |
