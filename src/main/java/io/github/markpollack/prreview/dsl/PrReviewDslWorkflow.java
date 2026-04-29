package io.github.markpollack.prreview.dsl;

import java.nio.file.Path;
import java.util.List;

import io.github.markpollack.prreview.model.PrContext;
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

/**
 * DSL-based PR review pipeline — structurally equivalent to {@code PrReviewWorkflow} but
 * composed using the Workflow DSL for declarative readability.
 *
 * <p>
 * Three sub-workflows:
 * <ul>
 * <li>{@code aiAssessment}: ExtractPrContext → parallel(assessCodeQuality,
 * assessBackport)</li>
 * <li>{@code assessAndReport}: T1 → aiAssessment → T2 → report</li>
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
			Step<PrContext, ?> assessCodeQuality, Step<PrContext, ?> assessBackport, QualityJudgeStep qualityJudgeStep,
			AssembleReportStep assembleReportStep, GenerateReportStep generateReport) {

		// T0 fail path: assemble report from context (T0 judgment written by
		// BuildGate.updateContext)
		Workflow<Object, Path> earlyReport = Workflow.<Object, Path>define("early-report")
			.step(assembleReportStep)
			.then(generateReport)
			.build();

		// AI assessment sub-workflow: extract PrContext, then run both assessments in
		// parallel (enrichment join). Each branch's updateContext() writes its result to
		// context; QualityJudgeStep reads both keys after the join.
		Workflow<Object, Object> aiAssessment = Workflow.<Object, Object>define("ai-assessment")
			.step(new ExtractPrContextStep())
			.parallel(assessCodeQuality, assessBackport)
			.build();

		// T0 pass path: T1 → AI assessment → T2 → report
		Workflow<Object, Path> assessAndReport = Workflow.<Object, Path>define("assess-and-report")
			.step(versionPatternStep)
			.then(aiAssessment)
			.then(qualityJudgeStep)
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

}
