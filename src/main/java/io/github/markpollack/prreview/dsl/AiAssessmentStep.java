package io.github.markpollack.prreview.dsl;

import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.steps.AssessBackportStep;
import io.github.markpollack.prreview.steps.AssessCodeQualityStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composite step that runs both AI assessment steps (code quality + backport) and
 * publishes results to context. Encapsulated as a single step to avoid sub-workflow
 * context isolation — the DSL executor calls {@code updateContext()} on this step, so the
 * assessment results propagate to downstream steps.
 */
public class AiAssessmentStep implements Step<Object, Object> {

	private static final Logger logger = LoggerFactory.getLogger(AiAssessmentStep.class);

	private final AssessCodeQualityStep assessCodeQuality;

	private final AssessBackportStep assessBackport;

	private volatile AssessmentResult lastQuality;

	private volatile AssessmentResult lastBackport;

	public AiAssessmentStep(AssessCodeQualityStep assessCodeQuality, AssessBackportStep assessBackport) {
		this.assessCodeQuality = assessCodeQuality;
		this.assessBackport = assessBackport;
	}

	@Override
	public String name() {
		return "ai-assessment";
	}

	@Override
	public Object execute(AgentContext ctx, Object input) {
		PrContext prContext = ctx.require(FetchPrContextStep.PR_CONTEXT);

		logger.info("Running AI code quality assessment");
		this.lastQuality = this.assessCodeQuality.execute(ctx, prContext);

		logger.info("Running AI backport assessment");
		this.lastBackport = this.assessBackport.execute(ctx, prContext);

		return input;
	}

	@Override
	public AgentContext updateContext(AgentContext ctx, Object output) {
		AgentContext.Builder builder = ctx.mutate();
		if (this.lastQuality != null) {
			builder.with(AssessCodeQualityStep.QUALITY_ASSESSMENT, this.lastQuality);
		}
		if (this.lastBackport != null) {
			builder.with(AssessBackportStep.BACKPORT_ASSESSMENT, this.lastBackport);
		}
		return builder.build();
	}

}
