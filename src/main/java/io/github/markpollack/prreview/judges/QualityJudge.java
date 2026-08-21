package io.github.markpollack.prreview.judges;

import java.util.ArrayList;
import java.util.List;

import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.agents.client.AgentClient;
import io.github.markpollack.agents.client.AgentClientResponse;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * T2 LLM judge — evaluates AI assessment quality and consistency.
 *
 * <p>
 * Evaluates the code-quality assessment and, when present, cross-checks it against the
 * (optional) backport assessment for consistency, flagging low-confidence or
 * contradictory results. The backport assessment is required only when the judge is
 * constructed with {@code requireBackport = true} (the default, for the spring-ai
 * reviewer); reviewers with no maintenance branches construct it with {@code false}. Uses
 * AgentClient for LLM evaluation.
 */
@Component
public class QualityJudge implements Judge {

	/** Metadata key for code quality {@link AssessmentResult}. */
	public static final String QUALITY_ASSESSMENT = "qualityAssessment";

	/** Metadata key for backport {@link AssessmentResult}. */
	public static final String BACKPORT_ASSESSMENT = "backportAssessment";

	private final AgentClient agentClient;

	private final boolean requireBackport;

	/**
	 * Defaults {@code requireBackport} to {@code true}: the spring-ai reviewer always
	 * supplies a backport assessment. Use {@link #QualityJudge(AgentClient, boolean)}
	 * with {@code false} for reviewers (e.g. agent-experiment) with no maintenance
	 * branches.
	 */
	@Autowired
	public QualityJudge(AgentClient agentClient) {
		this(agentClient, true);
	}

	public QualityJudge(AgentClient agentClient, boolean requireBackport) {
		this.agentClient = agentClient;
		this.requireBackport = requireBackport;
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		AssessmentResult quality = extract(context, QUALITY_ASSESSMENT, AssessmentResult.class);
		AssessmentResult backport = extract(context, BACKPORT_ASSESSMENT, AssessmentResult.class);

		List<Check> checks = new ArrayList<>();

		checkAssessmentPresent("quality-present", quality, checks);
		if (this.requireBackport) {
			checkAssessmentPresent("backport-present", backport, checks);
		}
		checkNotError("quality-no-error", quality, checks);
		checkNotError("backport-no-error", backport, checks);
		checkConsistency(quality, backport, checks);

		boolean allPassed = checks.stream().allMatch(Check::passed);
		double score = computeScore(quality, backport, allPassed);

		return Judgment.verdict(allPassed).score(score).reasoning(buildReasoning(checks, score)).checks(checks).build();
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
		// No backport assessment (e.g. a repo with no maintenance branches): the quality
		// assessment carries full weight rather than being diluted by a 0.5 default.
		if (backport == null) {
			return qualityScore;
		}
		return (qualityScore * 0.7) + (backport.score() * 0.3);
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
