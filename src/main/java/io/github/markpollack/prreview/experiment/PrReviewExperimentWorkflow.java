package io.github.markpollack.prreview.experiment;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BiFunction;

import io.github.markpollack.journal.Journal;
import io.github.markpollack.journal.Run;
import io.github.markpollack.prreview.dsl.AssembleReportStep;
import io.github.markpollack.prreview.dsl.DslContextKeys;
import io.github.markpollack.prreview.dsl.ExtractPrContextStep;
import io.github.markpollack.prreview.judges.BuildJudge;
import io.github.markpollack.prreview.judges.QualityJudge;
import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.RebaseResult;
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
import io.github.markpollack.workflow.flows.workflow.JudgeGate;
import io.github.markpollack.workflow.flows.workflow.RunOptions;
import io.github.markpollack.workflow.flows.workflow.Workflow;
import io.github.markpollack.workflow.flows.workflow.WorkflowExecutor;
import io.github.markpollack.workflow.journal.WorkflowJournal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.TierPolicy;

/**
 * The agent-experiment reviewer pipeline — a modern generalization of
 * {@code dsl.PrReviewDslWorkflow} for the {@code markpollack/agent-experiment}
 * repository.
 *
 * <p>
 * <strong>Spring-assembled (DD-15).</strong> This class holds the pipeline shape + the
 * two {@link JudgeGate} context mappers; it is wired from Spring beans by
 * {@code AgentExperimentReviewerConfig} under the {@code agent-experiment} profile (it
 * replaces the retired no-Spring {@code PrReviewExperimentRunner} hand-wiring). The
 * assess step is injected as a {@link Step} interface so the profile plugs in the
 * {@code KbConsultingAssessStep} (and any reviewer could swap a different assess
 * implementation).
 *
 * <p>
 * This is the same shape as the DSL workflow but built on the new mapper-enabled
 * {@link JudgeGate} (workflow-flows 0.11.0-SNAPSHOT) instead of the bespoke
 * {@code dsl.BuildGate} / {@code dsl.QualityJudgeStep}. The version/backport tiers
 * ({@code VersionPatternStep}/{@code VersionPatternJudge}, {@code AssessBackportStep})
 * are dropped.
 *
 * <pre>
 * contextPhase: FetchPrContextStep -&gt; RebaseStep -&gt; ConflictDetectionStep -&gt; RunTestsStep
 *   -&gt; JudgeGate(buildJury, 0.5, buildMapper)
 *        onFail -&gt; earlyReport (AssembleReportStep -&gt; GenerateReportStep)
 *        onPass -&gt; ExtractPrContextStep -&gt; AssessCodeQualityStep
 *                  -&gt; JudgeGate(qualityJury, 0.7, qualityMapper)
 *                       onPass/onFail -&gt; AssembleReportStep -&gt; GenerateReportStep
 * </pre>
 *
 * <p>
 * The two {@link JudgeGate} context mappers are lifted verbatim from
 * {@code dsl.BuildGate.evaluate} and {@code dsl.QualityJudgeStep.execute}: they read the
 * deterministic step outputs from the {@link AgentContext} and republish them as
 * {@link JudgmentContext} metadata under the judge-defined keys, so {@link BuildJudge}
 * and {@link QualityJudge} receive their typed inputs.
 */
@Agent("pr-review-experiment")
@Description("KB-consulting PR review pipeline for markpollack/agent-experiment")
public class PrReviewExperimentWorkflow implements AgentHandler<Integer, Path> {

	/**
	 * Default target repository (replaces the hardcoded
	 * {@code spring-projects/spring-ai}).
	 */
	public static final String DEFAULT_REPO = "markpollack/agent-experiment";

	/**
	 * Default local clone used for rebase/test execution and the Claude working
	 * directory.
	 */
	public static final String DEFAULT_REPO_DIR = "/home/mark/projects/agent-experiment";

	/** Default cost ceiling (USD) for a single review run. */
	public static final double DEFAULT_MAX_COST_USD = 5.0;

	private static final double BUILD_THRESHOLD = 0.5;

	private static final double QUALITY_THRESHOLD = 0.7;

	private static final Logger logger = LoggerFactory.getLogger(PrReviewExperimentWorkflow.class);

	private final Workflow<Integer, Path> pipeline;

	private final String repo;

	private final double maxCostUsd;

	/**
	 * Convenience constructor: targets {@link #DEFAULT_REPO} with a
	 * {@link #DEFAULT_MAX_COST_USD} budget.
	 */
	public PrReviewExperimentWorkflow(FetchPrContextStep fetchPrContext, RebaseStep rebaseStep,
			ConflictDetectionStep conflictDetection, RunTestsStep runTests, Step<PrContext, AssessmentResult> assess,
			BuildJudge buildJudge, QualityJudge qualityJudge, GenerateReportStep generateReport) {
		this(fetchPrContext, rebaseStep, conflictDetection, runTests, assess, buildJudge, qualityJudge, generateReport,
				DEFAULT_REPO, DEFAULT_MAX_COST_USD);
	}

	public PrReviewExperimentWorkflow(FetchPrContextStep fetchPrContext, RebaseStep rebaseStep,
			ConflictDetectionStep conflictDetection, RunTestsStep runTests, Step<PrContext, AssessmentResult> assess,
			BuildJudge buildJudge, QualityJudge qualityJudge, GenerateReportStep generateReport, String repo,
			double maxCostUsd) {

		this.repo = repo;
		this.maxCostUsd = maxCostUsd;

		// Two single-tier juries (the spring-ai version/backport tiers are intentionally
		// absent).
		Jury buildJury = JuryFactory.builder()
			.addJudge(0, buildJudge)
			.tierPolicy(0, TierPolicy.REJECT_ON_ANY_FAIL)
			.build()
			.build();
		Jury qualityJury = JuryFactory.builder()
			.addJudge(0, qualityJudge)
			.tierPolicy(0, TierPolicy.FINAL_TIER)
			.build()
			.build();

		// Mapper-enabled gates: the mapper populates JudgmentContext metadata from
		// AgentContext.
		JudgeGate<Object> buildGate = new JudgeGate<>(buildJury, BUILD_THRESHOLD, buildMapper());
		JudgeGate<Object> qualityGate = new JudgeGate<>(qualityJury, QUALITY_THRESHOLD, qualityMapper());

		Workflow<Integer, Object> contextPhase = Workflow.<Integer, Object>define("context-phase")
			.step(fetchPrContext)
			.then(rebaseStep)
			.then(conflictDetection)
			.then(runTests)
			.build();

		// onPass: pull PrContext from context, assess code quality, gate on it, then
		// report.
		Workflow<Object, Path> assessAndReport = Workflow.<Object, Path>define("assess-and-report")
			.step(new ExtractPrContextStep())
			.then(assess)
			.gate(qualityGate)
			.onPass(reportWorkflow("quality-pass-report", generateReport))
			.onFail(reportWorkflow("quality-fail-report", generateReport))
			.end()
			.build();

		// onFail: short-circuit straight to a report explaining the broken build.
		Workflow<Object, Path> earlyReport = reportWorkflow("early-report", generateReport);

		this.pipeline = Workflow.<Integer, Path>define("pr-review-experiment")
			.step(contextPhase)
			.gate(buildGate)
			.onPass(assessAndReport)
			.onFail(earlyReport)
			.end()
			.build();
	}

	@Override
	public Path handle(AgentContext ctx, Integer prNumber) {
		logger.info("=== PR Review Pipeline (experiment): {} PR #{} ===", this.repo, prNumber);

		AgentContext seedCtx = ctx.mutate()
			.with(DslContextKeys.JUDGMENTS, List.of())
			.with(DslContextKeys.ASSESSMENTS, List.of())
			.with(DslContextKeys.OVERALL_VERDICT, "PASS")
			.build();

		try (Run run = Journal.run("pr-review-experiment").name("PR #" + prNumber).config("repo", this.repo).start()) {
			WorkflowExecutor executor = new WorkflowExecutor(WorkflowJournal.forRun(run));
			return executor.execute(this.pipeline.graph(), seedCtx, prNumber, RunOptions.maxCost(this.maxCostUsd));
		}
	}

	public Workflow<Integer, Path> pipeline() {
		return this.pipeline;
	}

	public String repo() {
		return this.repo;
	}

	private static Workflow<Object, Path> reportWorkflow(String name, GenerateReportStep generateReport) {
		return Workflow.<Object, Path>define(name).step(new AssembleReportStep()).then(generateReport).build();
	}

	/**
	 * Lifted from {@code dsl.BuildGate#evaluate}: reads the deterministic context-phase
	 * outputs and republishes them as {@link BuildJudge} metadata keys.
	 */
	private static BiFunction<AgentContext, Object, JudgmentContext> buildMapper() {
		return (ctx, output) -> {
			RebaseResult rebase = ctx.get(RebaseStep.REBASE_RESULT).orElse(null);
			ConflictReport conflicts = ctx.get(ConflictDetectionStep.CONFLICT_REPORT).orElse(null);
			BuildResult build = ctx.get(RunTestsStep.BUILD_RESULT).orElse(null);

			JudgmentContext.Builder builder = JudgmentContext.builder()
				.goal("Build health evaluation")
				.agentOutput("Build evaluation for PR")
				.executionTime(Duration.ZERO)
				.startedAt(Instant.now());
			putIfNotNull(builder, BuildJudge.REBASE_RESULT, rebase);
			putIfNotNull(builder, BuildJudge.CONFLICT_REPORT, conflicts);
			putIfNotNull(builder, BuildJudge.BUILD_RESULT, build);
			return builder.build();
		};
	}

	/**
	 * Lifted from {@code dsl.QualityJudgeStep#execute}, with the backport metadata
	 * dropped: reads the AI quality assessment and republishes it as the
	 * {@link QualityJudge} metadata key.
	 */
	private static BiFunction<AgentContext, Object, JudgmentContext> qualityMapper() {
		return (ctx, output) -> {
			var quality = ctx.get(AssessCodeQualityStep.QUALITY_ASSESSMENT).orElse(null);

			JudgmentContext.Builder builder = JudgmentContext.builder()
				.goal("Quality evaluation")
				.agentOutput("Quality meta-judge for PR")
				.executionTime(Duration.ZERO)
				.startedAt(Instant.now());
			putIfNotNull(builder, QualityJudge.QUALITY_ASSESSMENT, quality);
			return builder.build();
		};
	}

	private static void putIfNotNull(JudgmentContext.Builder builder, String key, Object value) {
		if (value != null) {
			builder.metadata(key, value);
		}
	}

}
