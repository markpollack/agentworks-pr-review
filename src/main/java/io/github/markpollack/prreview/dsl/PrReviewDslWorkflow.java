package io.github.markpollack.prreview.dsl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.markpollack.prreview.config.WorkshopProperties;
import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.steps.AssessBackportStep;
import io.github.markpollack.prreview.steps.AssessCodeQualityStep;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.GenerateReportStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.core.AgentHandler;
import io.github.markpollack.workflow.core.Description;
import io.github.markpollack.workflow.flows.Step;
import io.github.markpollack.workflow.flows.agent.Agent;
import io.github.markpollack.workflow.flows.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.judge.result.Judgment;

/**
 * DSL-based PR review pipeline — structurally equivalent to {@code PrReviewWorkflow} but
 * composed using the Workflow DSL for declarative readability.
 *
 * <p>
 * Three sub-workflows:
 * <ul>
 * <li>{@code contextGathering}: fetch → rebase → conflicts → tests → fix/retest →
 * cleanup</li>
 * <li>{@code assessAndReport}: T1 → branch(skipAi) → parallel AI → T2 → report</li>
 * <li>{@code earlyReport}: T0 failure path → report</li>
 * </ul>
 *
 * <p>
 * The T0 gate is the structural pivot: onPass runs {@code assessAndReport}, onFail runs
 * {@code earlyReport}. Both produce {@code Path}.
 */
@Agent("pr-review-dsl")
@Description("DSL-based PR review pipeline")
public class PrReviewDslWorkflow implements AgentHandler<Integer, Path> {

	private static final Logger logger = LoggerFactory.getLogger(PrReviewDslWorkflow.class);

	private final Workflow<Integer, Path> pipeline;

	public PrReviewDslWorkflow(FetchPrContextStep fetchPrContext, RebaseStep rebaseStep,
			ConflictDetectionStep conflictDetection, RunTestsStep runTests, FixAndRetestStep fixAndRetestStep,
			CleanupStep cleanupStep, BuildGate buildGate, VersionPatternStep versionPatternStep,
			AssessCodeQualityStep assessCodeQuality, AssessBackportStep assessBackport,
			QualityJudgeStep qualityJudgeStep, AssembleReportStep assembleReportStep, GenerateReportStep generateReport,
			WorkshopProperties workshopProperties) {

		// Step that records the T0 judgment into context (shared by both gate paths)
		Step<Object, Object> recordT0Judgment = new RecordJudgmentStep("record-t0-judgment", buildGate::lastJudgment);

		// T0 fail path: record judgment + generate report
		// (inherits main workflow context, so PR_CONTEXT etc. are available)
		Workflow<Object, Path> earlyReport = Workflow.<Object, Path>define("early-report")
			.step(recordT0Judgment)
			.then(assembleReportStep)
			.then(generateReport)
			.build();

		// AI assessment sub-workflow (runs inside the skipAi branch)
		Workflow<Object, Object> aiAssessment = Workflow.<Object, Object>define("ai-assessment")
			.step(Step.named("pr-context-bridge", (ctx, __) -> ctx.require(FetchPrContextStep.PR_CONTEXT)))
			.parallel(assessCodeQuality, assessBackport)
			.then(qualityJudgeStep)
			.build();

		// T0 pass path: T1 → AI assessment → report
		// (inherits main workflow context, so PR_CONTEXT etc. are available)
		Workflow<Object, Path> assessAndReport = Workflow.<Object, Path>define("assess-and-report")
			.step(recordT0Judgment)
			.then(versionPatternStep)
			.branch(__ -> workshopProperties.skipAi())
			.then(Step.named("skip-ai", (ctx, in) -> in))
			.otherwise(aiAssessment)
			.then(assembleReportStep)
			.then(generateReport)
			.build();

		// Main pipeline — context-gathering steps are flattened here so their
		// updateContext() writes (PR_CONTEXT, REBASE_RESULT, etc.) stay in the
		// main workflow's context. Sub-workflows inherit this context at gate time.
		this.pipeline = Workflow.<Integer, Path>define("pr-review")
			.step(fetchPrContext)
			.then(rebaseStep)
			.then(conflictDetection)
			.then(runTests)
			.then(fixAndRetestStep)
			.then(cleanupStep)
			.gate(buildGate)
			.onPass(assessAndReport)
			.onFail(earlyReport)
			.end()
			.build();
	}

	@Override
	public Path handle(AgentContext ctx, Integer prNumber) {
		logger.info("=== PR Review Pipeline (DSL): PR #{} ===", prNumber);

		AgentContext seedCtx = ctx.mutate()
			.with(DslContextKeys.JUDGMENTS, List.of())
			.with(DslContextKeys.ASSESSMENTS, List.of())
			.with(DslContextKeys.OVERALL_VERDICT, "PASS")
			.build();

		return this.pipeline.execute(seedCtx, prNumber);
	}

	public Workflow<Integer, Path> pipeline() {
		return this.pipeline;
	}

	/**
	 * Helper step that reads a judgment from a supplier and appends it to the JUDGMENTS
	 * context list. Used to bridge the BuildGate's volatile field into context.
	 */
	static class RecordJudgmentStep implements Step<Object, Object> {

		private final String stepName;

		private final java.util.function.Supplier<Judgment> judgmentSupplier;

		private volatile Judgment captured;

		RecordJudgmentStep(String stepName, java.util.function.Supplier<Judgment> judgmentSupplier) {
			this.stepName = stepName;
			this.judgmentSupplier = judgmentSupplier;
		}

		@Override
		public String name() {
			return this.stepName;
		}

		@Override
		public Object execute(AgentContext ctx, Object input) {
			this.captured = this.judgmentSupplier.get();
			return input;
		}

		@Override
		public AgentContext updateContext(AgentContext ctx, Object output) {
			if (this.captured != null) {
				List<Judgment> existing = ctx.get(DslContextKeys.JUDGMENTS).orElse(List.of());
				List<Judgment> updated = new ArrayList<>(existing);
				updated.add(this.captured);
				return ctx.mutate().with(DslContextKeys.JUDGMENTS, List.copyOf(updated)).build();
			}
			return ctx;
		}

	}

}
