package io.github.markpollack.prreview.dsl;

import java.time.Instant;
import java.util.List;

import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.FixResult;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.prreview.model.ReviewReport;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.Step;
import org.springaicommunity.judge.result.Judgment;

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
		List<AssessmentResult> assessments = ctx.get(DslContextKeys.ASSESSMENTS).orElse(List.of());
		List<Judgment> judgments = ctx.get(DslContextKeys.JUDGMENTS).orElse(List.of());

		return new ReviewReport(prContext, rebase, conflicts, build, fixResult, assessments, judgments, Instant.now());
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
