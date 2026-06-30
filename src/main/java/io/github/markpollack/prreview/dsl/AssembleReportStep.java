package io.github.markpollack.prreview.dsl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.FixResult;
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
 * Assembles a {@link ReviewReport} from all intermediate results stored in context.
 *
 * <p>
 * Handles missing values gracefully — the early-report path will have empty assessments
 * and judgments.
 */
public class AssembleReportStep implements Step<Object, ReviewReport> {

	@Override
	public String name() {
		return "assemble-report";
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
		if (verdict.subVerdicts() != null && !verdict.subVerdicts().isEmpty()) {
			for (Verdict sub : verdict.subVerdicts()) {
				collectJudgments(sub, out);
			}
		}
		else if (verdict.individual() != null && !verdict.individual().isEmpty()) {
			out.addAll(verdict.individual());
		}
		else if (verdict.aggregated() != null) {
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
