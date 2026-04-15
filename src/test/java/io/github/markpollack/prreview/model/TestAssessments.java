package io.github.markpollack.prreview.model;

import java.time.Instant;
import java.util.List;

import org.springaicommunity.judge.result.Check;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;
import org.springaicommunity.judge.score.BooleanScore;
import org.springaicommunity.judge.score.NumericalScore;

/**
 * Factory methods for sample assessment results and judgments used in tests.
 */
public final class TestAssessments {

	private TestAssessments() {
	}

	// -- AssessmentResult factories --

	/** Passing quality assessment with good score. */
	public static AssessmentResult qualityPass() {
		return new AssessmentResult("QualityJudge", JudgmentStatus.PASS, 0.85, "Clean code with good test coverage",
				List.of("Well-structured error handling", "Integration test included"));
	}

	/** Failing quality assessment with specific findings. */
	public static AssessmentResult qualityFail() {
		return new AssessmentResult("QualityJudge", JudgmentStatus.FAIL, 0.35, "Multiple issues found",
				List.of("Missing null checks on public API", "No test for error paths", "Hardcoded timeout value"));
	}

	/** Passing version pattern assessment — no Boot 3→4 issues. */
	public static AssessmentResult versionPatternPass() {
		return new AssessmentResult("VersionPatternJudge", JudgmentStatus.PASS, 1.0, "No version pattern issues found",
				List.of());
	}

	/** Failing version pattern assessment — Boot 3→4 migration issue. */
	public static AssessmentResult versionPatternFail() {
		return new AssessmentResult("VersionPatternJudge", JudgmentStatus.FAIL, 0.0,
				"Uses deprecated javax.* imports (Boot 4 requires jakarta.*)",
				List.of("javax.servlet.http.HttpServletRequest → jakarta.servlet.http.HttpServletRequest",
						"javax.persistence.Entity → jakarta.persistence.Entity"));
	}

	/** Passing build judge — compile + tests green. */
	public static AssessmentResult buildPass() {
		return new AssessmentResult("BuildJudge", JudgmentStatus.PASS, 1.0, "Build and tests passed", List.of());
	}

	/** Failing build judge — test failure. */
	public static AssessmentResult buildFail() {
		return new AssessmentResult("BuildJudge", JudgmentStatus.FAIL, 0.0, "Test failures detected",
				List.of("OllamaToolSupportTest.shouldHandleUnsupportedTools: expected FAIL but was PASS"));
	}

	// -- Judgment factories (from agent-judge-core) --

	/** T0 passing judgment with metadata. */
	public static Judgment buildPassJudgment() {
		return Judgment.builder()
			.score(new BooleanScore(true))
			.status(JudgmentStatus.PASS)
			.reasoning("Build judge: 4/4 checks passed")
			.check(Check.pass("rebase-clean"))
			.check(Check.pass("no-complex-conflicts"))
			.check(Check.pass("build-executed"))
			.check(Check.pass("tests-passed"))
			.metadata("judge_name", "Build Judge")
			.metadata("tier", "T0")
			.build();
	}

	/** T1 passing judgment with metadata. */
	public static Judgment versionPatternPassJudgment() {
		return Judgment.builder()
			.score(new BooleanScore(true))
			.status(JudgmentStatus.PASS)
			.reasoning("Version pattern judge: no migration anti-patterns detected")
			.metadata("judge_name", "Version Pattern Judge")
			.metadata("tier", "T1")
			.build();
	}

	/** T2 passing judgment with metadata. */
	public static Judgment qualityPassJudgment() {
		return Judgment.builder()
			.score(NumericalScore.normalized(0.895))
			.status(JudgmentStatus.PASS)
			.reasoning("Quality judge: 5/5 checks passed, composite score 0.90")
			.check(Check.pass("quality-present"))
			.check(Check.pass("backport-present"))
			.check(Check.pass("quality-no-error"))
			.check(Check.pass("backport-no-error"))
			.check(Check.pass("consistency"))
			.metadata("judge_name", "Quality Judge")
			.metadata("tier", "T2")
			.build();
	}

	/** T0 failing judgment with metadata. */
	public static Judgment buildFailJudgment() {
		return Judgment.builder()
			.score(new BooleanScore(false))
			.status(JudgmentStatus.FAIL)
			.reasoning("Build judge: 3/4 checks passed. Failures: tests-passed (Tests failed)")
			.check(Check.pass("rebase-clean"))
			.check(Check.pass("no-complex-conflicts"))
			.check(Check.pass("build-executed"))
			.check(Check.fail("tests-passed", "Tests failed (exit code non-zero)"))
			.metadata("judge_name", "Build Judge")
			.metadata("tier", "T0")
			.build();
	}

	/** Passing judgment (legacy — delegates to buildPassJudgment). */
	public static Judgment passingJudgment() {
		return buildPassJudgment();
	}

	/** Failing judgment (legacy — delegates to buildFailJudgment). */
	public static Judgment failingJudgment() {
		return buildFailJudgment();
	}

	// -- BuildResult factories --

	/** Successful build with one module. */
	public static BuildResult buildSuccess() {
		return new BuildResult(true, false, List.of("mcp-spring-webflux"), "BUILD SUCCESS\n\nTotal time: 45s", 45000);
	}

	/** Failed build with test failures. */
	public static BuildResult buildFailure() {
		return new BuildResult(false, false, List.of("spring-ai-ollama"), "Tests run: 12, Failures: 2\n\nBUILD FAILURE",
				30000);
	}

	// -- ConflictReport factories --

	/** Report with one simple and one complex conflict. */
	public static ConflictReport mixedConflicts() {
		return new ConflictReport(
				List.of(new ConflictFile("pom.xml", Classification.SIMPLE, "version bump conflict"),
						new ConflictFile("src/main/java/Config.java", Classification.COMPLEX,
								"overlapping refactors in configuration class")),
				true, "2 conflicts: 1 simple (auto-resolvable), 1 complex (needs human review)");
	}

	// -- ReviewReport factories --

	/** Complete passing report — all phases succeeded, all three tiers. */
	public static ReviewReport passingReport() {
		return new ReviewReport(TestPrContexts.pr5774(), RebaseResult.clean("fix/889-body-error-propagation"),
				ConflictReport.clean(), buildSuccess(), null, List.of(buildPass(), versionPatternPass(), qualityPass()),
				List.of(buildPassJudgment(), versionPatternPassJudgment(), qualityPassJudgment()),
				Instant.parse("2026-04-08T20:00:00Z"));
	}

	/**
	 * Complete passing report with reviews and comments — exercises discussion section.
	 */
	public static ReviewReport passingReportWithReviews() {
		return new ReviewReport(TestPrContexts.prWithReviews(), RebaseResult.clean("feature/ollama-function-calling"),
				ConflictReport.clean(), buildSuccess(), null, List.of(buildPass(), versionPatternPass(), qualityPass()),
				List.of(buildPassJudgment(), versionPatternPassJudgment(), qualityPassJudgment()),
				Instant.parse("2026-04-08T20:00:00Z"));
	}

	/** Report where build failed — only T0 judgment, AI assessments absent. */
	public static ReviewReport failedBuildReport() {
		return new ReviewReport(TestPrContexts.pr5774(), RebaseResult.clean("fix/889-body-error-propagation"),
				ConflictReport.clean(), buildFailure(), null, List.of(buildFail()), List.of(buildFailJudgment()),
				Instant.parse("2026-04-08T20:00:00Z"));
	}

}
