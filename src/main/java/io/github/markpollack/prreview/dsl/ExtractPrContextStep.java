package io.github.markpollack.prreview.dsl;

import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.Step;

/**
 * Reads {@link PrContext} from context and passes it as the current value to the next
 * step. Replaces anonymous lambda steps that performed
 * {@code ctx.require(FetchPrContextStep.PR_CONTEXT)}.
 */
public class ExtractPrContextStep implements Step<Object, PrContext> {

	@Override
	public String name() {
		return "extract-pr-context";
	}

	@Override
	public PrContext execute(AgentContext ctx, Object input) {
		return ctx.require(FetchPrContextStep.PR_CONTEXT);
	}

	@Override
	public Class<?> inputType() {
		return Object.class;
	}

	@Override
	public Class<?> outputType() {
		return PrContext.class;
	}

}
