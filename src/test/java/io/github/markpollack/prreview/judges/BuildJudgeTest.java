package io.github.markpollack.prreview.judges;

import java.util.List;
import java.util.Map;

import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.RebaseResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Check;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;

class BuildJudgeTest {

	private final BuildJudge judge = new BuildJudge();

	private JudgmentContext contextWith(RebaseResult rebase, ConflictReport conflicts, BuildResult build) {
		JudgmentContext.Builder builder = JudgmentContext.builder().goal("Build evaluation");
		if (rebase != null) {
			builder.metadata(BuildJudge.REBASE_RESULT, rebase);
		}
		if (conflicts != null) {
			builder.metadata(BuildJudge.CONFLICT_REPORT, conflicts);
		}
		if (build != null) {
			builder.metadata(BuildJudge.BUILD_RESULT, build);
		}
		return builder.build();
	}

	@Nested
	class FullyGreenBuild {

		@Test
		void shouldPassWhenCleanRebaseAndTestsSucceed() {
			JudgmentContext ctx = contextWith(RebaseResult.clean("feature-branch"), ConflictReport.clean(),
					new BuildResult(true, false, List.of("core"), "BUILD SUCCESS", 5000));

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(judgment.pass()).isTrue();
			assertThat(judgment.checks()).hasSize(4);
			assertThat(judgment.checks()).allMatch(Check::passed);
			assertThat(judgment.reasoning()).contains("4/4 checks passed");
		}

	}

	@Nested
	class RebaseFailure {

		@Test
		void shouldFailWhenRebaseHasConflicts() {
			JudgmentContext ctx = contextWith(RebaseResult.conflict("feature-branch", List.of("pom.xml", "App.java")),
					ConflictReport.clean(), new BuildResult(true, false, List.of("."), "BUILD SUCCESS", 3000));

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(judgment.pass()).isFalse();
			assertThat(failedCheckNames(judgment)).contains("rebase-clean");
			assertThat(judgment.reasoning()).contains("rebase-clean");
		}

		@Test
		void shouldFailWhenRebaseResultMissing() {
			JudgmentContext ctx = contextWith(null, ConflictReport.clean(),
					new BuildResult(true, false, List.of("."), "BUILD SUCCESS", 3000));

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(failedCheckNames(judgment)).contains("rebase-clean");
		}

	}

	@Nested
	class ComplexConflicts {

		@Test
		void shouldFailWhenComplexConflictsDetected() {
			ConflictReport complex = new ConflictReport(
					List.of(new io.github.markpollack.prreview.model.ConflictFile("App.java",
							io.github.markpollack.prreview.model.Classification.COMPLEX, "overlapping edits")),
					true, "1 conflict: 1 complex (needs human review)");

			JudgmentContext ctx = contextWith(RebaseResult.clean("feature-branch"), complex,
					BuildResult.skippedBuild());

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(failedCheckNames(judgment)).contains("no-complex-conflicts", "build-executed");
		}

		@Test
		void shouldPassWhenOnlySimpleConflicts() {
			ConflictReport simpleOnly = new ConflictReport(
					List.of(new io.github.markpollack.prreview.model.ConflictFile("pom.xml",
							io.github.markpollack.prreview.model.Classification.SIMPLE, "version bump")),
					false, "1 conflict: 1 simple (auto-resolvable)");

			JudgmentContext ctx = contextWith(RebaseResult.clean("feature-branch"), simpleOnly,
					new BuildResult(true, false, List.of("."), "BUILD SUCCESS", 2000));

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		}

	}

	@Nested
	class BuildFailure {

		@Test
		void shouldFailWhenBuildSkipped() {
			JudgmentContext ctx = contextWith(RebaseResult.clean("feature-branch"), ConflictReport.clean(),
					BuildResult.skippedBuild());

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(failedCheckNames(judgment)).contains("build-executed");
		}

		@Test
		void shouldFailWhenTestsFail() {
			JudgmentContext ctx = contextWith(RebaseResult.clean("feature-branch"), ConflictReport.clean(),
					new BuildResult(false, false, List.of("core"), "TESTS FAILED", 8000));

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(failedCheckNames(judgment)).contains("tests-passed");
			assertThat(judgment.reasoning()).contains("tests-passed");
		}

		@Test
		void shouldFailWhenBuildResultMissing() {
			JudgmentContext ctx = contextWith(RebaseResult.clean("feature-branch"), ConflictReport.clean(), null);

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(failedCheckNames(judgment)).contains("build-executed");
		}

	}

	@Nested
	class CheckCounting {

		@Test
		void shouldHaveFourChecksWhenBuildExecuted() {
			JudgmentContext ctx = contextWith(RebaseResult.clean("feature-branch"), ConflictReport.clean(),
					new BuildResult(true, false, List.of("."), "BUILD SUCCESS", 1000));

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.checks()).hasSize(4)
				.extracting(Check::name)
				.containsExactly("rebase-clean", "no-complex-conflicts", "build-executed", "tests-passed");
		}

		@Test
		void shouldOmitTestsPassedCheckWhenBuildSkipped() {
			JudgmentContext ctx = contextWith(RebaseResult.clean("feature-branch"), ConflictReport.clean(),
					BuildResult.skippedBuild());

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.checks()).extracting(Check::name).doesNotContain("tests-passed");
		}

	}

	private static List<String> failedCheckNames(Judgment judgment) {
		return judgment.checks().stream().filter(c -> !c.passed()).map(Check::name).toList();
	}

}
