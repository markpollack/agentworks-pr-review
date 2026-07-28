package io.github.markpollack.prreview.v3;

import java.time.Duration;

import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.prreview.dsl.AssembleEarlyReportStep;
import io.github.markpollack.prreview.dsl.AssembleReportStep;
import io.github.markpollack.prreview.dsl.CleanupStep;
import io.github.markpollack.prreview.dsl.QualityJudgeStep;
import io.github.markpollack.prreview.dsl.VersionPatternStep;
import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.FixPolicy;
import io.github.markpollack.prreview.steps.AssessBackportStep;
import io.github.markpollack.prreview.steps.AssessCodeQualityStep;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.FixTestsStep;
import io.github.markpollack.prreview.steps.GenerateReportStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.prreview.steps.ShouldAttemptFixStep;
import io.github.markpollack.workflow.flows.v3.ContextKey;
import io.github.markpollack.workflow.flows.v3.Workflow;
import io.github.markpollack.workflow.spec.v3.WorkflowSpec;

/**
 * This application's PR-review pipeline, authored in the {@code workflow/v3alpha} DSL.
 *
 * <p>
 * The leaves arrive injected, one parameter per bean, so the call sites read
 * {@code .then(rebaseOnMain)} and never {@code .then(new RebaseStep(...))}: the workflow
 * reads as composition, and {@link PrReviewV3Config} is the one place that constructs
 * anything. Every one of them is a bean this application already had — the v3 leaf and
 * the v1 leaf are two doors on one class (see {@code FetchPrContextStep}) — with two
 * exceptions that the graph itself created: {@link ShouldAttemptFixStep}, which is the
 * fix/skip predicate the v1 composite hid inside a private method, and
 * {@link AssembleEarlyReportStep}, which is the half of the report bean the v1 chain
 * could not tell apart.
 *
 * <h2>What replaced the hand-authored spec</h2> The served {@code workflow/v3alpha}
 * document used to be a JSON resource maintained by hand beside the code it described. It
 * is now emitted from this method, so the code <em>is</em> the workflow: renaming a step
 * renames a node, and inserting one is a spec change nobody has to remember to make.
 *
 * <h2>Two config sites, and the difference between them</h2> {@code workshop.fix-tests}
 * reaches the artifact as the fix decision's {@link FixPolicy} {@code config}, because it
 * changes what the pipeline <em>does</em>: in v1 an operator could turn AI repairs on and
 * the approved artifact would not change by one byte. The threshold, the timeouts and the
 * budget stay written here, because they are authoring decisions rather than deployment
 * ones — the test that a value belongs in the artifact is whether changing it should
 * require re-approving the workflow, and for both kinds the answer is yes.
 */
public final class PrReviewWorkflowV3 {

	/**
	 * What crosses the graph without an edge: the build result is written by whichever of
	 * {@code run-tests} / {@code retest} ran last, and both the gate and the report read
	 * it. A node binding cannot say that — {@code retest} is inside the fix arm and may
	 * not have run — so the convergence rides context, which is what context is for.
	 * Declaring the two writes is the whole of what this file says about it.
	 */
	static final ContextKey<BuildResult> BUILD_RESULT = ContextKey.of("build.result", BuildResult.class);

	private PrReviewWorkflowV3() {
	}

	public static WorkflowSpec build(FetchPrContextStep fetchPrContext, RebaseStep rebaseOnMain,
			ConflictDetectionStep detectConflicts, RunTestsStep runTests, ShouldAttemptFixStep shouldAttemptFix,
			FixTestsStep fixTests, CleanupStep cleanupBranch, VersionPatternStep versionPatternCheck,
			AssessCodeQualityStep assessCodeQuality, AssessBackportStep assessBackport, QualityJudgeStep qualityJudge,
			AssembleReportStep assembleReport, AssembleEarlyReportStep assembleEarlyReport,
			GenerateReportStep generateReport, Jury buildHealthJury, FixPolicy fixPolicy) {

		return Workflow.define("pr-review")
			.version("1.0.0")
			.label("app", "agentworks-pr-review")
			.budget(spend -> spend.maxCostUsd(5.00).maxDuration(Duration.ofMinutes(30)))
			.retry(2, Duration.ofSeconds(1), 2.0)

			.step(fetchPrContext)
			.then(rebaseOnMain)
			.then(detectConflicts)
			// The timeout is the operation's, not this node's: run-tests is placed twice,
			// and a node-level bound would leave 'retest' running unbounded.
			.then(runTests)
			.timingOutAfter(Duration.ofMinutes(10))
			.atEveryUse()
			.writing(BUILD_RESULT)

			.decision(shouldAttemptFix)
			.configuring(fixPolicy)
			.on("fix",
					fix -> fix.then(fixTests)
						.retrying(1, Duration.ofSeconds(5), 2.0)
						.then("retest", runTests)
						.writing(BUILD_RESULT))
			.on("skip")

			.then(cleanupBranch)

			// The gateRef is derived, not written: 'judge:' is a fact about the injected
			// bean's type and the rest is this node's authored name.
			.gate("build-health", buildHealthJury)
			.atThreshold(0.7)
			.submitting(BuildResult.class)
			.onPass(pass -> pass.then(versionPatternCheck)
				.parallel("assess")
				.of(assessCodeQuality, assessBackport)
				.merged()
				.maxConcurrency(2)
				.then(qualityJudge)
				// Placed once, so the bound is this node's.
				.then(assembleReport)
				.timingOutAfter(Duration.ofMinutes(2))
				.then(generateReport)
				.terminate("published"))
			.onFail(early -> early.then(assembleEarlyReport)
				.timingOutAfter(Duration.ofMinutes(2))
				.then("generate-early-report", generateReport)
				.terminate("early-published"))

			.build();
	}

}
