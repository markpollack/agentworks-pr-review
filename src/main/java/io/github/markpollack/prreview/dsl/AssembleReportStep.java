package io.github.markpollack.prreview.dsl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.FixResult;
import io.github.markpollack.prreview.model.FullReportRequest;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.prreview.model.ReviewReport;
import io.github.markpollack.prreview.steps.AssessCodeQualityStep;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.Step;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;

/**
 * Assembles a {@link ReviewReport} on the arm where the build-health gate passed.
 *
 * <p>
 * <b>One class became two.</b> In v1 this bean served both arms and read eight context
 * keys, handling absence "gracefully" — which is what a step does when it cannot tell
 * which path reached it. The v3 leaves dispatch different values, so the early arm is
 * {@link AssembleEarlyReportStep} and the difference between the reports is a type
 * difference rather than a run-time absence check.
 *
 * <p>
 * <b>What the v3 leaf does not receive</b>: the raw assessment list and the judgment
 * trail. Both are collections of a domain type, and the surface derives a binding from a
 * producer of the <em>element</em> type only inside a {@code fork}; a
 * {@code List<AssessmentResult>} parameter names no producer the graph can find. What the
 * arm does carry is the quality verdict that summarizes them and the gate's own
 * {@link io.github.markpollack.workflow.spec.v3.envelope.GateEvaluation}.
 */
public class AssembleReportStep implements Step<Object, ReviewReport>,
		io.github.markpollack.workflow.flows.v3.Step<FullReportRequest, ReviewReport> {

	@Override
	public String name() {
		return "assemble-report";
	}

	@Override
	public ReviewReport execute(FullReportRequest request) {
		return new ReviewReport(request.context(), request.rebase(), request.conflicts(), request.build(), null,
				List.of(), List.of(), Instant.now());
	}

	@Override
	public ReviewReport execute(AgentContext ctx, Object input) {
		PrContext prContext = ctx.require(FetchPrContextStep.PR_CONTEXT);
		RebaseResult rebase = ctx.get(RebaseStep.REBASE_RESULT)
			.orElseGet(() -> new RebaseResult(false, "unknown", List.of(), "Missing rebase result"));
		ConflictReport conflicts = ctx.get(ConflictDetectionStep.CONFLICT_REPORT).orElseGet(ConflictReport::clean);
		BuildResult build = ctx.get(RunTestsStep.BUILD_RESULT).orElseGet(BuildResult::skippedBuild);
		FixResult fixResult = ctx.get(DslContextKeys.FIX_RESULT).orElse(null);
		// Assessments: the legacy ASSESSMENTS list (the hand-rolled DSL gates collected
		// them)
		// plus the assess step's own output key — the modern experiment reviewer writes
		// only
		// the latter.
		List<AssessmentResult> assessments = new ArrayList<>(ctx.get(DslContextKeys.ASSESSMENTS).orElse(List.of()));
		ctx.get(AssessCodeQualityStep.QUALITY_ASSESSMENT)
			.filter(a -> !assessments.contains(a))
			.ifPresent(assessments::add);

		// Judgments: the legacy JUDGMENTS list (hand-rolled gates) plus the
		// framework-standard
		// verdict trail WorkflowExecutor writes for every mapper-enabled JudgeGate.
		List<Judgment> judgments = new ArrayList<>(ctx.get(DslContextKeys.JUDGMENTS).orElse(List.of()));
		for (Object verdict : ctx.get(AgentContext.JUDGE_VERDICTS).orElse(List.of())) {
			collectJudgments(verdict, judgments);
		}

		return new ReviewReport(prContext, rebase, conflicts, build, fixResult, assessments, judgments, Instant.now());
	}

	/**
	 * Flattens a verdict tree (CascadedJury -&gt; tiers -&gt; judges) into its individual
	 * judgments.
	 */
	private static void collectJudgments(Object verdictObj, List<Judgment> out) {
		if (!(verdictObj instanceof Verdict verdict)) {
			return;
		}
		if (!verdict.compositeAttempts().isEmpty()) {
			for (var attempt : verdict.compositeAttempts()) {
				if (attempt.verdict() != null) {
					collectJudgments(attempt.verdict(), out);
				}
			}
		}
		else if (!verdict.individual().isEmpty()) {
			out.addAll(verdict.individual());
		}
		else {
			out.add(verdict.aggregated());
		}
	}

	@Override
	public Class<?> inputType() {
		return Object.class;
	}

	@Override
	public Class<?> outputType() {
		return ReviewReport.class;
	}

}
