package io.github.markpollack.prreview.dsl;

import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.Step;

/**
 * Passthrough step that cleans up the review branch after tests complete.
 */
public class CleanupStep implements Step<Object, Object> {

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
		PrContext prContext = ctx.require(FetchPrContextStep.PR_CONTEXT);
		this.rebaseStep.cleanup(prContext);
		return input;
	}

}
