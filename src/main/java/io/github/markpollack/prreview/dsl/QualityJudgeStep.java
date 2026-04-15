package io.github.markpollack.prreview.dsl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.github.markpollack.prreview.judges.QualityJudge;
import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.steps.AssessBackportStep;
import io.github.markpollack.prreview.steps.AssessCodeQualityStep;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;

/**
 * T2 step: evaluates AI assessment quality. Reads assessment results published by the
 * parallel AI steps and delegates to {@link QualityJudge}.
 *
 * <p>
 * Invariant: this step runs AFTER the parallel join, so there is no concurrent
 * modification risk on the JUDGMENTS or ASSESSMENTS lists.
 */
public class QualityJudgeStep implements Step<Object, Object> {

	private static final Logger logger = LoggerFactory.getLogger(QualityJudgeStep.class);

	private final QualityJudge qualityJudge;

	private volatile Judgment lastJudgment;

	private volatile AssessmentResult lastQuality;

	private volatile AssessmentResult lastBackport;

	public QualityJudgeStep(QualityJudge qualityJudge) {
		this.qualityJudge = qualityJudge;
	}

	@Override
	public String name() {
		return "quality-judge";
	}

	@Override
	public Object execute(AgentContext ctx, Object input) {
		AssessmentResult quality = ctx.get(AssessCodeQualityStep.QUALITY_ASSESSMENT).orElse(null);
		AssessmentResult backport = ctx.get(AssessBackportStep.BACKPORT_ASSESSMENT).orElse(null);

		this.lastQuality = quality;
		this.lastBackport = backport;

		JudgmentContext.Builder builder = JudgmentContext.builder()
			.goal("Quality evaluation")
			.agentOutput("Quality meta-judge for PR")
			.executionTime(Duration.ZERO)
			.startedAt(Instant.now());
		putIfNotNull(builder, QualityJudge.QUALITY_ASSESSMENT, quality);
		putIfNotNull(builder, QualityJudge.BACKPORT_ASSESSMENT, backport);

		Judgment judgment = this.qualityJudge.judge(builder.build());
		this.lastJudgment = withMeta(judgment);

		logger.info("T2 verdict: {} — {}", judgment.status(), judgment.reasoning());

		return input;
	}

	@Override
	public AgentContext updateContext(AgentContext ctx, Object output) {
		AgentContext.Builder builder = ctx.mutate();

		if (this.lastJudgment != null) {
			List<Judgment> existingJudgments = ctx.get(DslContextKeys.JUDGMENTS).orElse(List.of());
			List<Judgment> updatedJudgments = new ArrayList<>(existingJudgments);
			updatedJudgments.add(this.lastJudgment);
			builder.with(DslContextKeys.JUDGMENTS, List.copyOf(updatedJudgments));

			if (this.lastJudgment.status() == JudgmentStatus.FAIL) {
				builder.with(DslContextKeys.OVERALL_VERDICT, "FAIL");
			}
		}

		List<AssessmentResult> existingAssessments = ctx.get(DslContextKeys.ASSESSMENTS).orElse(List.of());
		List<AssessmentResult> updatedAssessments = new ArrayList<>(existingAssessments);
		if (this.lastQuality != null) {
			updatedAssessments.add(this.lastQuality);
		}
		if (this.lastBackport != null) {
			updatedAssessments.add(this.lastBackport);
		}
		if (!updatedAssessments.isEmpty()) {
			builder.with(DslContextKeys.ASSESSMENTS, List.copyOf(updatedAssessments));
		}

		return builder.build();
	}

	private static Judgment withMeta(Judgment judgment) {
		return Judgment.builder()
			.score(judgment.score())
			.status(judgment.status())
			.reasoning(judgment.reasoning())
			.checks(judgment.checks())
			.metadata("judge_name", "Quality Judge")
			.metadata("tier", "T2")
			.build();
	}

	private static void putIfNotNull(JudgmentContext.Builder builder, String key, Object value) {
		if (value != null) {
			builder.metadata(key, value);
		}
	}

}
