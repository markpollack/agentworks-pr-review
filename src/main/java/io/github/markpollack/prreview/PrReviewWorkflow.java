package io.github.markpollack.prreview;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.github.markpollack.prreview.config.WorkshopProperties;
import io.github.markpollack.prreview.judges.BuildJudge;
import io.github.markpollack.prreview.judges.QualityJudge;
import io.github.markpollack.prreview.judges.VersionPatternJudge;
import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.prreview.model.ReviewReport;
import io.github.markpollack.prreview.steps.AssessBackportStep;
import io.github.markpollack.prreview.steps.AssessCodeQualityStep;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.GenerateReportStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.workflow.flows.AgentContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;

import org.springframework.stereotype.Component;

/**
 * Orchestrates the complete PR review pipeline.
 *
 * <p>
 * Three-phase execution:
 * <ol>
 * <li>Deterministic context gathering (GitHub API, git rebase, conflict detection,
 * tests)</li>
 * <li>Judge cascade (T0: BuildJudge → T1: VersionPatternJudge → AI assessment → T2:
 * QualityJudge)</li>
 * <li>Report generation (markdown)</li>
 * </ol>
 *
 * <p>
 * Uses explicit step-by-step orchestration rather than the Workflow DSL to maximize
 * readability for workshop participants. Each step call and gate evaluation is visible
 * and debuggable.
 */
@Component
public class PrReviewWorkflow {

	private static final Logger logger = LoggerFactory.getLogger(PrReviewWorkflow.class);

	private final FetchPrContextStep fetchPrContext;

	private final RebaseStep rebaseStep;

	private final ConflictDetectionStep conflictDetection;

	private final RunTestsStep runTests;

	private final BuildJudge buildJudge;

	private final VersionPatternJudge versionPatternJudge;

	private final AssessCodeQualityStep assessCodeQuality;

	private final AssessBackportStep assessBackport;

	private final QualityJudge qualityJudge;

	private final GenerateReportStep generateReport;

	private final WorkshopProperties workshopProperties;

	public PrReviewWorkflow(FetchPrContextStep fetchPrContext, RebaseStep rebaseStep,
			ConflictDetectionStep conflictDetection, RunTestsStep runTests, BuildJudge buildJudge,
			VersionPatternJudge versionPatternJudge, AssessCodeQualityStep assessCodeQuality,
			AssessBackportStep assessBackport, QualityJudge qualityJudge, GenerateReportStep generateReport,
			WorkshopProperties workshopProperties) {
		this.fetchPrContext = fetchPrContext;
		this.rebaseStep = rebaseStep;
		this.conflictDetection = conflictDetection;
		this.runTests = runTests;
		this.buildJudge = buildJudge;
		this.versionPatternJudge = versionPatternJudge;
		this.assessCodeQuality = assessCodeQuality;
		this.assessBackport = assessBackport;
		this.qualityJudge = qualityJudge;
		this.generateReport = generateReport;
		this.workshopProperties = workshopProperties;
	}

	/**
	 * Executes the full PR review pipeline for the given PR number.
	 * @param prNumber the GitHub PR number to review
	 * @return path to the generated report
	 */
	public Path execute(int prNumber) {
		logger.info("=== PR Review Pipeline: PR #{} ===", prNumber);
		AgentContext ctx = AgentContext.create();
		List<Judgment> judgments = new ArrayList<>();
		List<AssessmentResult> assessments = new ArrayList<>();

		// ── Phase 1: Deterministic Context Gathering ──────────────────
		logger.info("── Phase 1: Deterministic Context Gathering ──");

		PrContext prContext = this.fetchPrContext.execute(ctx, prNumber);
		ctx = this.fetchPrContext.updateContext(ctx, prContext);

		RebaseResult rebase = this.rebaseStep.execute(ctx, prContext);
		ConflictReport conflicts = this.conflictDetection.execute(ctx, rebase);
		BuildResult build = this.runTests.execute(ctx, conflicts);

		// ── T0 Gate: Build Judge (deterministic) ──────────────────────
		logger.info("── T0 Gate: Build Judge ──");
		Judgment t0 = evaluateBuildJudge(rebase, conflicts, build);
		judgments.add(t0);
		logger.info("T0 verdict: {} — {}", t0.status(), t0.reasoning());

		if (t0.status() == JudgmentStatus.FAIL) {
			logger.warn("T0 FAIL — skipping T1, AI assessment, and T2");
			return generateFinalReport(ctx, prContext, rebase, conflicts, build, assessments, judgments);
		}

		// ── T1 Gate: Version Pattern Judge (deterministic) ───────────
		logger.info("── T1 Gate: Version Pattern Judge ──");
		Judgment t1 = evaluateVersionPatternJudge(prContext);
		judgments.add(t1);
		logger.info("T1 verdict: {} — {}", t1.status(), t1.reasoning());

		if (t1.status() == JudgmentStatus.FAIL) {
			logger.warn("T1 FAIL — skipping AI assessment and T2");
			return generateFinalReport(ctx, prContext, rebase, conflicts, build, assessments, judgments);
		}

		// ── Phase 2: AI Assessment ───────────────────────────────────
		if (this.workshopProperties.skipAi()) {
			logger.info("── Phase 2: AI Assessment SKIPPED (skip-ai=true) ──");
		}
		else {
			logger.info("── Phase 2: AI Assessment ──");

			AssessmentResult quality = this.assessCodeQuality.execute(ctx, prContext);
			ctx = this.assessCodeQuality.updateContext(ctx, quality);
			assessments.add(quality);

			AssessmentResult backport = this.assessBackport.execute(ctx, prContext);
			ctx = this.assessBackport.updateContext(ctx, backport);
			assessments.add(backport);

			// ── T2 Gate: Quality Judge (LLM meta-judge) ──────────────
			logger.info("── T2 Gate: Quality Judge ──");
			Judgment t2 = evaluateQualityJudge(quality, backport);
			judgments.add(t2);
			logger.info("T2 verdict: {} — {}", t2.status(), t2.reasoning());
		}

		// ── Phase 3: Report Generation ───────────────────────────────
		return generateFinalReport(ctx, prContext, rebase, conflicts, build, assessments, judgments);
	}

	private Judgment evaluateBuildJudge(RebaseResult rebase, ConflictReport conflicts, BuildResult build) {
		JudgmentContext.Builder builder = JudgmentContext.builder()
			.goal("Build health evaluation")
			.agentOutput("Build evaluation for PR")
			.executionTime(Duration.ZERO)
			.startedAt(Instant.now());
		putIfNotNull(builder, BuildJudge.REBASE_RESULT, rebase);
		putIfNotNull(builder, BuildJudge.CONFLICT_REPORT, conflicts);
		putIfNotNull(builder, BuildJudge.BUILD_RESULT, build);
		return this.buildJudge.judge(builder.build());
	}

	private Judgment evaluateVersionPatternJudge(PrContext prContext) {
		JudgmentContext.Builder builder = JudgmentContext.builder()
			.goal("Version pattern evaluation")
			.agentOutput("Version pattern check for PR")
			.executionTime(Duration.ZERO)
			.startedAt(Instant.now());
		putIfNotNull(builder, VersionPatternJudge.PR_CONTEXT, prContext);
		return this.versionPatternJudge.judge(builder.build());
	}

	private Judgment evaluateQualityJudge(@Nullable AssessmentResult quality, @Nullable AssessmentResult backport) {
		JudgmentContext.Builder builder = JudgmentContext.builder()
			.goal("Quality evaluation")
			.agentOutput("Quality meta-judge for PR")
			.executionTime(Duration.ZERO)
			.startedAt(Instant.now());
		putIfNotNull(builder, QualityJudge.QUALITY_ASSESSMENT, quality);
		putIfNotNull(builder, QualityJudge.BACKPORT_ASSESSMENT, backport);
		return this.qualityJudge.judge(builder.build());
	}

	private Path generateFinalReport(AgentContext ctx, PrContext prContext, RebaseResult rebase,
			ConflictReport conflicts, BuildResult build, List<AssessmentResult> assessments, List<Judgment> judgments) {
		logger.info("── Phase 3: Report Generation ──");
		ReviewReport report = new ReviewReport(prContext, rebase, conflicts, build, assessments, judgments,
				Instant.now());
		return this.generateReport.execute(ctx, report);
	}

	private static void putIfNotNull(JudgmentContext.Builder builder, String key, @Nullable Object value) {
		if (value != null) {
			builder.metadata(key, value);
		}
	}

}
