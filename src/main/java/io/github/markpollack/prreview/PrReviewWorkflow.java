package io.github.markpollack.prreview;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.markpollack.journal.Journal;
import io.github.markpollack.journal.Run;
import io.github.markpollack.journal.event.CostBreakdown;
import io.github.markpollack.journal.event.CustomEvent;
import io.github.markpollack.journal.event.LLMCallEvent;
import io.github.markpollack.journal.event.StateChangeEvent;
import io.github.markpollack.journal.event.TimingInfo;
import io.github.markpollack.journal.event.TokenUsage;
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
import io.github.markpollack.prreview.model.FixResult;
import io.github.markpollack.prreview.steps.AssessBackportStep;
import io.github.markpollack.prreview.steps.AssessCodeQualityStep;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.FixTestsStep;
import io.github.markpollack.prreview.steps.GenerateReportStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.workflow.core.AgentContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agents.client.AgentClientResponse;
import org.springaicommunity.agents.model.AgentResponseMetadata;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Check;
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

	private final FixTestsStep fixTests;

	private final BuildJudge buildJudge;

	private final VersionPatternJudge versionPatternJudge;

	private final AssessCodeQualityStep assessCodeQuality;

	private final AssessBackportStep assessBackport;

	private final QualityJudge qualityJudge;

	private final GenerateReportStep generateReport;

	private final WorkshopProperties workshopProperties;

	private String lastRunId;

	public PrReviewWorkflow(FetchPrContextStep fetchPrContext, RebaseStep rebaseStep,
			ConflictDetectionStep conflictDetection, RunTestsStep runTests, FixTestsStep fixTests,
			BuildJudge buildJudge, VersionPatternJudge versionPatternJudge, AssessCodeQualityStep assessCodeQuality,
			AssessBackportStep assessBackport, QualityJudge qualityJudge, GenerateReportStep generateReport,
			WorkshopProperties workshopProperties) {
		this.fetchPrContext = fetchPrContext;
		this.rebaseStep = rebaseStep;
		this.conflictDetection = conflictDetection;
		this.runTests = runTests;
		this.fixTests = fixTests;
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

		try (Run run = Journal.run("pr-review")
			.name("pr-" + prNumber)
			.task(String.valueOf(prNumber))
			.config("repo", "spring-projects/spring-ai")
			.start()) {

			this.lastRunId = run.id();
			AgentContext ctx = AgentContext.create();
			List<Judgment> judgments = new ArrayList<>();
			List<AssessmentResult> assessments = new ArrayList<>();
			String overallVerdict = "PASS";

			// ── Phase 1: Deterministic Context Gathering ──────────────────
			logger.info("── Phase 1: Deterministic Context Gathering ──");

			PrContext prContext = executeStep(run, "fetch-pr-context",
					() -> this.fetchPrContext.execute(ctx, prNumber));
			AgentContext ctx2 = this.fetchPrContext.updateContext(ctx, prContext);

			RebaseResult rebase = executeStep(run, "rebase", () -> this.rebaseStep.execute(ctx2, prContext));
			ConflictReport conflicts = executeStep(run, "conflict-detection",
					() -> this.conflictDetection.execute(ctx2, rebase));
			BuildResult build = executeStep(run, "run-tests", () -> this.runTests.execute(ctx2, conflicts));

			// ── Fix-Tests: AI attempts to fix failing tests ──────────────
			if (shouldAttemptFix(rebase, conflicts, build)) {
				logger.info("── Fix-Tests: AI attempting to fix test failures ──");
				run.logEvent(CustomEvent.of("step-started", Map.of("step", "fix-tests")));
				Instant fixStart = Instant.now();
				FixResult fixResult = this.fixTests.execute(ctx2, build);
				long fixMs = Duration.between(fixStart, Instant.now()).toMillis();
				emitLlmCallEvent(run, this.fixTests.lastResponse(), "fix-tests");
				run.logEvent(CustomEvent.of("step-completed", Map.of("step", "fix-tests", "durationMs", fixMs,
						"attempted", fixResult.attempted(), "fixed", fixResult.fixed())));
				logger.info("Fix-tests result: attempted={}, fixed={}, summary={}", fixResult.attempted(),
						fixResult.fixed(), fixResult.summary());

				// Re-run tests after AI fix attempt
				logger.info("── Re-running tests after AI fix ──");
				build = executeStep(run, "run-tests-post-fix", () -> this.runTests.execute(ctx2, conflicts));
			}

			// Clean up review branch now that tests (and any fix attempts) are done
			this.rebaseStep.cleanup(prContext);

			run.logEvent(StateChangeEvent.of("context-gathering", "judge-cascade", "Phase 1 complete"));

			// ── T0 Gate: Build Judge (deterministic) ──────────────────────
			logger.info("── T0 Gate: Build Judge ──");
			run.logEvent(CustomEvent.of("judge-started", Map.of("tier", "T0", "judge", "build-judge")));
			Judgment t0 = evaluateBuildJudge(rebase, conflicts, build);
			judgments.add(t0);
			run.logEvent(CustomEvent.of("judge-verdict",
					Map.of("tier", "T0", "status", t0.status().name(), "reasoning", t0.reasoning())));
			logger.info("T0 verdict: {} — {}", t0.status(), t0.reasoning());

			if (t0.status() == JudgmentStatus.FAIL) {
				logger.warn("T0 FAIL — skipping T1, AI assessment, and T2");
				overallVerdict = "FAIL";
				Path report = generateFinalReport(run, ctx2, prContext, rebase, conflicts, build, assessments,
						judgments);
				run.setSummary("verdict", overallVerdict);
				run.setSummary("prNumber", prNumber);
				return report;
			}

			// ── T1 Gate: Version Pattern Judge (deterministic) ───────────
			logger.info("── T1 Gate: Version Pattern Judge ──");
			run.logEvent(CustomEvent.of("judge-started", Map.of("tier", "T1", "judge", "version-pattern-judge")));
			Judgment t1 = evaluateVersionPatternJudge(prContext);
			judgments.add(t1);

			List<Check> failedChecks = t1.checks().stream().filter(c -> !c.passed()).toList();
			for (Check check : failedChecks) {
				run.logEvent(CustomEvent.of("version-pattern-finding",
						Map.of("check", check.name(), "message", check.message())));
			}
			run.logEvent(CustomEvent.of("judge-verdict",
					Map.of("tier", "T1", "status", t1.status().name(), "findings", failedChecks.size())));
			logger.info("T1 verdict: {} — {}", t1.status(), t1.reasoning());

			if (t1.status() == JudgmentStatus.FAIL) {
				logger.warn("T1 FAIL — skipping AI assessment and T2");
				overallVerdict = "FAIL";
				Path report = generateFinalReport(run, ctx2, prContext, rebase, conflicts, build, assessments,
						judgments);
				run.setSummary("verdict", overallVerdict);
				run.setSummary("prNumber", prNumber);
				return report;
			}

			// ── Phase 2: AI Assessment ───────────────────────────────────
			AgentContext ctx3 = ctx2;
			if (this.workshopProperties.skipAi()) {
				logger.info("── Phase 2: AI Assessment SKIPPED (skip-ai=true) ──");
			}
			else {
				run.logEvent(StateChangeEvent.of("judge-cascade", "ai-assessment", "T0 and T1 passed"));
				logger.info("── Phase 2: AI Assessment ──");

				run.logEvent(CustomEvent.of("step-started", Map.of("step", "assess-code-quality")));
				Instant qualityStart = Instant.now();
				AssessmentResult quality = this.assessCodeQuality.execute(ctx3, prContext);
				long qualityMs = Duration.between(qualityStart, Instant.now()).toMillis();
				emitLlmCallEvent(run, this.assessCodeQuality.lastResponse(), "assess-code-quality");
				run.logEvent(CustomEvent.of("step-completed", Map.of("step", "assess-code-quality", "durationMs",
						qualityMs, "status", quality.status().name())));
				ctx3 = this.assessCodeQuality.updateContext(ctx3, quality);
				assessments.add(quality);

				run.logEvent(CustomEvent.of("step-started", Map.of("step", "assess-backport")));
				Instant backportStart = Instant.now();
				AssessmentResult backport = this.assessBackport.execute(ctx3, prContext);
				long backportMs = Duration.between(backportStart, Instant.now()).toMillis();
				emitLlmCallEvent(run, this.assessBackport.lastResponse(), "assess-backport");
				run.logEvent(CustomEvent.of("step-completed", Map.of("step", "assess-backport", "durationMs",
						backportMs, "status", backport.status().name())));
				ctx3 = this.assessBackport.updateContext(ctx3, backport);
				assessments.add(backport);

				// ── T2 Gate: Quality Judge (LLM meta-judge) ──────────────
				logger.info("── T2 Gate: Quality Judge ──");
				run.logEvent(CustomEvent.of("judge-started", Map.of("tier", "T2", "judge", "quality-judge")));
				Judgment t2 = evaluateQualityJudge(quality, backport);
				judgments.add(t2);
				run.logEvent(CustomEvent.of("judge-verdict",
						Map.of("tier", "T2", "status", t2.status().name(), "reasoning", t2.reasoning())));
				logger.info("T2 verdict: {} — {}", t2.status(), t2.reasoning());

				if (t2.status() == JudgmentStatus.FAIL) {
					overallVerdict = "FAIL";
				}
			}

			// ── Phase 3: Report Generation ───────────────────────────────
			run.logEvent(StateChangeEvent.of(this.workshopProperties.skipAi() ? "judge-cascade" : "ai-assessment",
					"report-generation", "Generating final report"));
			Path report = generateFinalReport(run, ctx3, prContext, rebase, conflicts, build, assessments, judgments);
			run.setSummary("verdict", overallVerdict);
			run.setSummary("prNumber", prNumber);
			return report;
		}
	}

	/**
	 * Returns the run ID from the last execution (package-private, for testing).
	 */
	String lastRunId() {
		return this.lastRunId;
	}

	private <T> T executeStep(Run run, String stepName, java.util.function.Supplier<T> action) {
		run.logEvent(CustomEvent.of("step-started", Map.of("step", stepName)));
		Instant start = Instant.now();
		T result = action.get();
		long elapsed = Duration.between(start, Instant.now()).toMillis();
		run.logEvent(CustomEvent.of("step-completed", Map.of("step", stepName, "durationMs", elapsed)));
		return result;
	}

	private void emitLlmCallEvent(Run run, @Nullable AgentClientResponse response, String stepName) {
		if (response == null) {
			return;
		}
		AgentResponseMetadata meta = response.getMetadata();
		Integer inputTokens = meta.get("inputTokens");
		Integer outputTokens = meta.get("outputTokens");
		Integer thinkingTokens = meta.get("thinkingTokens");
		Double costUsd = meta.get("totalCostUsd");
		run.logEvent(LLMCallEvent.builder()
			.model(meta.getModel())
			.tokenUsage(TokenUsage.of(inputTokens != null ? inputTokens : 0, outputTokens != null ? outputTokens : 0,
					thinkingTokens != null ? thinkingTokens : 0))
			.cost(costUsd != null ? CostBreakdown.of(costUsd) : null)
			.timing(TimingInfo.of(meta.getDuration().toMillis()))
			.metadata(Map.of("step", stepName))
			.build());
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
		return withJudgeMeta(this.buildJudge.judge(builder.build()), "Build Judge", "T0");
	}

	private Judgment evaluateVersionPatternJudge(PrContext prContext) {
		JudgmentContext.Builder builder = JudgmentContext.builder()
			.goal("Version pattern evaluation")
			.agentOutput("Version pattern check for PR")
			.executionTime(Duration.ZERO)
			.startedAt(Instant.now());
		putIfNotNull(builder, VersionPatternJudge.PR_CONTEXT, prContext);
		return withJudgeMeta(this.versionPatternJudge.judge(builder.build()), "Version Pattern Judge", "T1");
	}

	private Judgment evaluateQualityJudge(@Nullable AssessmentResult quality, @Nullable AssessmentResult backport) {
		JudgmentContext.Builder builder = JudgmentContext.builder()
			.goal("Quality evaluation")
			.agentOutput("Quality meta-judge for PR")
			.executionTime(Duration.ZERO)
			.startedAt(Instant.now());
		putIfNotNull(builder, QualityJudge.QUALITY_ASSESSMENT, quality);
		putIfNotNull(builder, QualityJudge.BACKPORT_ASSESSMENT, backport);
		return withJudgeMeta(this.qualityJudge.judge(builder.build()), "Quality Judge", "T2");
	}

	private static Judgment withJudgeMeta(Judgment judgment, String judgeName, String tier) {
		return Judgment.builder()
			.score(judgment.score())
			.status(judgment.status())
			.reasoning(judgment.reasoning())
			.checks(judgment.checks())
			.metadata("judge_name", judgeName)
			.metadata("tier", tier)
			.build();
	}

	private Path generateFinalReport(Run run, AgentContext ctx, PrContext prContext, RebaseResult rebase,
			ConflictReport conflicts, BuildResult build, List<AssessmentResult> assessments, List<Judgment> judgments) {
		logger.info("── Phase 3: Report Generation ──");
		run.logEvent(CustomEvent.of("step-started", Map.of("step", "generate-report")));
		Instant start = Instant.now();
		ReviewReport report = new ReviewReport(prContext, rebase, conflicts, build, assessments, judgments,
				Instant.now());
		Path path = this.generateReport.execute(ctx, report);
		long elapsed = Duration.between(start, Instant.now()).toMillis();
		run.logEvent(CustomEvent.of("step-completed", Map.of("step", "generate-report", "durationMs", elapsed)));
		return path;
	}

	private boolean shouldAttemptFix(RebaseResult rebase, ConflictReport conflicts, BuildResult build) {
		return this.workshopProperties.fixTests() && rebase.success() && !conflicts.hasComplexConflicts()
				&& !build.skipped() && !build.success();
	}

	private static void putIfNotNull(JudgmentContext.Builder builder, String key, @Nullable Object value) {
		if (value != null) {
			builder.metadata(key, value);
		}
	}

}
