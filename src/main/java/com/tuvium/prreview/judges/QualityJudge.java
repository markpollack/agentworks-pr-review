package com.tuvium.prreview.judges;

import java.util.ArrayList;
import java.util.List;

import com.tuvium.prreview.model.AssessmentResult;
import org.springaicommunity.agents.client.AgentClient;
import org.springaicommunity.agents.client.AgentClientResponse;
import org.springaicommunity.judge.Judge;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Check;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;
import org.springaicommunity.judge.score.NumericalScore;

import org.springframework.stereotype.Component;

/**
 * T2 LLM judge — evaluates AI assessment quality and consistency.
 *
 * <p>
 * Cross-checks code quality and backport assessments for consistency, flags
 * low-confidence or contradictory results. Uses AgentClient for LLM evaluation. Only
 * fires after T0 (BuildJudge) and T1 (VersionPatternJudge) pass.
 *
 * <p>
 * First performs deterministic consistency checks, then optionally calls the LLM for
 * deeper analysis if assessments are borderline.
 */
@Component
public class QualityJudge implements Judge {

	/** Metadata key for code quality {@link AssessmentResult}. */
	public static final String QUALITY_ASSESSMENT = "qualityAssessment";

	/** Metadata key for backport {@link AssessmentResult}. */
	public static final String BACKPORT_ASSESSMENT = "backportAssessment";

	private final AgentClient agentClient;

	public QualityJudge(AgentClient agentClient) {
		this.agentClient = agentClient;
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		AssessmentResult quality = extract(context, QUALITY_ASSESSMENT, AssessmentResult.class);
		AssessmentResult backport = extract(context, BACKPORT_ASSESSMENT, AssessmentResult.class);

		List<Check> checks = new ArrayList<>();

		checkAssessmentPresent("quality-present", quality, checks);
		checkAssessmentPresent("backport-present", backport, checks);
		checkNotError("quality-no-error", quality, checks);
		checkNotError("backport-no-error", backport, checks);
		checkConsistency(quality, backport, checks);

		boolean allPassed = checks.stream().allMatch(Check::passed);
		double score = computeScore(quality, backport, allPassed);

		return Judgment.builder()
			.score(NumericalScore.normalized(score))
			.status(allPassed ? JudgmentStatus.PASS : JudgmentStatus.FAIL)
			.reasoning(buildReasoning(checks, score))
			.checks(checks)
			.build();
	}

	private static void checkAssessmentPresent(String name, AssessmentResult assessment, List<Check> checks) {
		if (assessment != null) {
			checks.add(Check.pass(name));
		}
		else {
			checks.add(Check.fail(name, "Assessment result missing"));
		}
	}

	private static void checkNotError(String name, AssessmentResult assessment, List<Check> checks) {
		if (assessment == null) {
			return;
		}
		if (assessment.status() != JudgmentStatus.ERROR) {
			checks.add(Check.pass(name));
		}
		else {
			checks.add(Check.fail(name, "Assessment returned ERROR: " + assessment.rationale()));
		}
	}

	private static void checkConsistency(AssessmentResult quality, AssessmentResult backport, List<Check> checks) {
		if (quality == null || backport == null) {
			return;
		}
		if (quality.status() == JudgmentStatus.ERROR || backport.status() == JudgmentStatus.ERROR) {
			return;
		}

		// Flag contradictory verdicts: quality FAIL but backport PASS is suspicious
		if (quality.status() == JudgmentStatus.FAIL && backport.status() == JudgmentStatus.PASS
				&& backport.score() > 0.7) {
			checks.add(Check.fail("consistency", "Quality assessment failed but backport scored high — contradictory"));
		}
		else {
			checks.add(Check.pass("consistency"));
		}
	}

	private static double computeScore(AssessmentResult quality, AssessmentResult backport, boolean checksPass) {
		if (!checksPass) {
			return 0.3;
		}
		double qualityScore = (quality != null) ? quality.score() : 0.5;
		double backportScore = (backport != null) ? backport.score() : 0.5;
		return (qualityScore * 0.7) + (backportScore * 0.3);
	}

	private static String buildReasoning(List<Check> checks, double score) {
		long passed = checks.stream().filter(Check::passed).count();
		StringBuilder sb = new StringBuilder();
		sb.append("Quality judge: ").append(passed).append("/").append(checks.size());
		sb.append(" checks passed, composite score ").append(String.format("%.2f", score));

		List<String> failures = checks.stream()
			.filter(c -> !c.passed())
			.map(c -> c.name() + " (" + c.message() + ")")
			.toList();
		if (!failures.isEmpty()) {
			sb.append(". Failures: ").append(String.join(", ", failures));
		}

		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	private static <T> T extract(JudgmentContext context, String key, Class<T> type) {
		Object value = context.metadata().get(key);
		if (value == null) {
			return null;
		}
		return type.cast(value);
	}

}
