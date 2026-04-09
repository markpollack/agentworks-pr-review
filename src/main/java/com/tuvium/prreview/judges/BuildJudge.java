package com.tuvium.prreview.judges;

import java.util.ArrayList;
import java.util.List;

import com.tuvium.prreview.model.BuildResult;
import com.tuvium.prreview.model.ConflictReport;
import com.tuvium.prreview.model.RebaseResult;
import org.springaicommunity.judge.Judge;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Check;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;
import org.springaicommunity.judge.score.BooleanScore;

import org.springframework.stereotype.Component;

/**
 * T0 deterministic judge — evaluates build health with no AI.
 *
 * <p>
 * Checks four conditions: clean rebase, no complex conflicts, build executed (not
 * skipped), and tests passed. FAIL blocks all downstream steps (T1, T2, AI assessment).
 * Each check is reported as a {@link Check} sub-assertion.
 *
 * <p>
 * Reads structured data from {@link JudgmentContext#metadata()} using well-known keys
 * ({@link #REBASE_RESULT}, {@link #CONFLICT_REPORT}, {@link #BUILD_RESULT}). The custom
 * workflow gate (DD-8) populates these from AgentContext.
 */
@Component
public class BuildJudge implements Judge {

	/** Metadata key for {@link RebaseResult}. */
	public static final String REBASE_RESULT = "rebaseResult";

	/** Metadata key for {@link ConflictReport}. */
	public static final String CONFLICT_REPORT = "conflictReport";

	/** Metadata key for {@link BuildResult}. */
	public static final String BUILD_RESULT = "buildResult";

	@Override
	public Judgment judge(JudgmentContext context) {
		RebaseResult rebase = extract(context, REBASE_RESULT, RebaseResult.class);
		ConflictReport conflicts = extract(context, CONFLICT_REPORT, ConflictReport.class);
		BuildResult build = extract(context, BUILD_RESULT, BuildResult.class);

		List<Check> checks = new ArrayList<>();

		checkRebase(rebase, checks);
		checkConflicts(conflicts, checks);
		checkBuildExecuted(build, checks);
		checkTestsPassed(build, checks);

		boolean allPassed = checks.stream().allMatch(Check::passed);

		return Judgment.builder()
			.score(new BooleanScore(allPassed))
			.status(allPassed ? JudgmentStatus.PASS : JudgmentStatus.FAIL)
			.reasoning(buildReasoning(checks, allPassed))
			.checks(checks)
			.build();
	}

	private static void checkRebase(RebaseResult rebase, List<Check> checks) {
		if (rebase == null) {
			checks.add(Check.fail("rebase-clean", "No rebase result available"));
			return;
		}
		if (rebase.success()) {
			checks.add(Check.pass("rebase-clean"));
		}
		else {
			checks
				.add(Check.fail("rebase-clean", "Rebase had conflicts in " + rebase.conflictFiles().size() + " files"));
		}
	}

	private static void checkConflicts(ConflictReport conflicts, List<Check> checks) {
		if (conflicts == null) {
			checks.add(Check.pass("no-complex-conflicts"));
			return;
		}
		if (!conflicts.hasComplexConflicts()) {
			checks.add(Check.pass("no-complex-conflicts"));
		}
		else {
			checks.add(Check.fail("no-complex-conflicts", conflicts.summary()));
		}
	}

	private static void checkBuildExecuted(BuildResult build, List<Check> checks) {
		if (build == null) {
			checks.add(Check.fail("build-executed", "No build result available"));
			return;
		}
		if (!build.skipped()) {
			checks.add(Check.pass("build-executed"));
		}
		else {
			checks.add(Check.fail("build-executed", "Build was skipped due to complex conflicts"));
		}
	}

	private static void checkTestsPassed(BuildResult build, List<Check> checks) {
		if (build == null || build.skipped()) {
			return;
		}
		if (build.success()) {
			checks.add(Check.pass("tests-passed"));
		}
		else {
			checks.add(Check.fail("tests-passed", "Tests failed (exit code non-zero)"));
		}
	}

	private static String buildReasoning(List<Check> checks, boolean allPassed) {
		long passed = checks.stream().filter(Check::passed).count();
		StringBuilder sb = new StringBuilder();
		sb.append("Build judge: ").append(passed).append("/").append(checks.size()).append(" checks passed");

		if (!allPassed) {
			sb.append(". Failures: ");
			List<String> failures = checks.stream()
				.filter(c -> !c.passed())
				.map(c -> c.name() + " (" + c.message() + ")")
				.toList();
			sb.append(String.join(", ", failures));
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
