package io.github.markpollack.prreview.dsl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.markpollack.prreview.judges.QualityJudge;
import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.QualityVerdict;
import io.github.markpollack.prreview.steps.AssessBackportStep;
import io.github.markpollack.prreview.steps.AssessCodeQualityStep;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * T2 step: evaluates AI assessment quality. Reads assessment results published by the
 * parallel AI steps and delegates to {@link QualityJudge}.
 *
 * <p>
 * Invariant: this step runs AFTER the parallel join, so there is no concurrent
 * modification risk on the JUDGMENTS or ASSESSMENTS lists.
 */
public class QualityJudgeStep
		implements Step<Object, Object>, io.github.markpollack.workflow.flows.v3.Step<Object, QualityVerdict> {

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
		judge(ctx.get(AssessCodeQualityStep.QUALITY_ASSESSMENT).orElse(null),
				ctx.get(AssessBackportStep.BACKPORT_ASSESSMENT).orElse(null));
		return input;
	}

	/**
	 * The v3 leaf. Its input is the assessment join, and it is untyped on the way in
	 * because it has to be: a merged join carries one entry per branch <em>node id</em>,
	 * and a node id is not a legal Java identifier, so no record can mirror the shape.
	 * What this does about it is read the assessments out <b>by type</b> rather than by
	 * node id — the leaf must not know what the branches were called.
	 */
	@Override
	public QualityVerdict execute(Object joined) {
		List<AssessmentResult> assessments = assessmentsIn(joined);
		return judge(byJudge(assessments, AssessCodeQualityStep.JUDGE_NAME),
				byJudge(assessments, AssessBackportStep.JUDGE_NAME));
	}

	private QualityVerdict judge(AssessmentResult quality, AssessmentResult backport) {
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

		return new QualityVerdict(judgment.status(), judgment.reasoning());
	}

	/**
	 * Every {@link AssessmentResult} reachable one level inside the join's value,
	 * whatever container the operation plane hands over.
	 */
	private static List<AssessmentResult> assessmentsIn(Object joined) {
		List<AssessmentResult> found = new ArrayList<>();
		collectAssessments(joined, found);
		return List.copyOf(found);
	}

	private static void collectAssessments(Object value, List<AssessmentResult> out) {
		if (value instanceof AssessmentResult assessment) {
			out.add(assessment);
		}
		else if (value instanceof Map<?, ?> map) {
			map.values().forEach(entry -> collectAssessments(entry, out));
		}
		else if (value instanceof Iterable<?> items) {
			items.forEach(entry -> collectAssessments(entry, out));
		}
	}

	private static AssessmentResult byJudge(List<AssessmentResult> assessments, String judgeName) {
		return assessments.stream().filter(a -> judgeName.equals(a.judgeName())).findFirst().orElse(null);
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
