package io.github.markpollack.prreview.dsl;

import io.github.markpollack.prreview.model.CleanupResult;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.Step;

/**
 * Cleans up the review branch after tests complete.
 *
 * <p>
 * <b>Two doors, one body.</b> The v3 leaf declares what this step does —
 * {@code PrContext → CleanupResult} — and the v1 leaf delegates to it, then returns its
 * input untouched because a v1 chain has no other way to carry the predecessor's value
 * past a step that produces nothing of its own.
 */
public class CleanupStep
		implements Step<Object, Object>, io.github.markpollack.workflow.flows.v3.Step<PrContext, CleanupResult> {

	private final RebaseStep rebaseStep;

	public CleanupStep(RebaseStep rebaseStep) {
		this.rebaseStep = rebaseStep;
	}

	@Override
	public String name() {
		return "cleanup-branch";
	}

	@Override
	public Object execute(AgentContext ctx, Object input) {
		execute(ctx.require(FetchPrContextStep.PR_CONTEXT));
		return input;
	}

	@Override
	public CleanupResult execute(PrContext prContext) {
		this.rebaseStep.cleanup(prContext);
		return new CleanupResult("review/pr-" + prContext.number(), true);
	}

}
