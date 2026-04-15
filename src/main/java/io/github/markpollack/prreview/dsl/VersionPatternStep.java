package io.github.markpollack.prreview.dsl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.github.markpollack.prreview.judges.VersionPatternJudge;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;

/**
 * T1 step: evaluates version migration patterns. This is NOT a gate — T1 failure records
 * the verdict and sets overall verdict to FAIL but does not short-circuit the pipeline.
 */
public class VersionPatternStep implements Step<Object, Object> {

	private static final Logger logger = LoggerFactory.getLogger(VersionPatternStep.class);

	private final VersionPatternJudge versionPatternJudge;

	private volatile Judgment lastJudgment;

	public VersionPatternStep(VersionPatternJudge versionPatternJudge) {
		this.versionPatternJudge = versionPatternJudge;
	}

	@Override
	public String name() {
		return "version-pattern-check";
	}

	@Override
	public Object execute(AgentContext ctx, Object input) {
		PrContext prContext = ctx.require(FetchPrContextStep.PR_CONTEXT);

		JudgmentContext.Builder builder = JudgmentContext.builder()
			.goal("Version pattern evaluation")
			.agentOutput("Version pattern check for PR")
			.executionTime(Duration.ZERO)
			.startedAt(Instant.now());
		builder.metadata(VersionPatternJudge.PR_CONTEXT, prContext);

		Judgment judgment = this.versionPatternJudge.judge(builder.build());
		this.lastJudgment = withMeta(judgment);

		logger.info("T1 verdict: {} — {}", judgment.status(), judgment.reasoning());

		return input;
	}

	@Override
	public AgentContext updateContext(AgentContext ctx, Object output) {
		AgentContext.Builder builder = ctx.mutate();

		if (this.lastJudgment != null) {
			List<Judgment> existing = ctx.get(DslContextKeys.JUDGMENTS).orElse(List.of());
			List<Judgment> updated = new ArrayList<>(existing);
			updated.add(this.lastJudgment);
			builder.with(DslContextKeys.JUDGMENTS, List.copyOf(updated));

			if (this.lastJudgment.status() == JudgmentStatus.FAIL) {
				builder.with(DslContextKeys.OVERALL_VERDICT, "FAIL");
			}
		}

		return builder.build();
	}

	private static Judgment withMeta(Judgment judgment) {
		return Judgment.builder()
			.score(judgment.score())
			.status(judgment.status())
			.reasoning(judgment.reasoning())
			.checks(judgment.checks())
			.metadata("judge_name", "Version Pattern Judge")
			.metadata("tier", "T1")
			.build();
	}

}
