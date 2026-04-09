package io.github.markpollack.prreview.judges;

import java.util.List;

import io.github.markpollack.prreview.model.AssessmentResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agents.client.AgentClient;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Check;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QualityJudgeTest {

	private final AgentClient agentClient = mock(AgentClient.class);

	private final QualityJudge judge = new QualityJudge(agentClient);

	private AssessmentResult quality(JudgmentStatus status, double score) {
		return new AssessmentResult("code-quality", status, score, "test rationale", List.of());
	}

	private AssessmentResult backport(JudgmentStatus status, double score) {
		return new AssessmentResult("backport", status, score, "test rationale", List.of());
	}

	private JudgmentContext contextWith(AssessmentResult quality, AssessmentResult backport) {
		JudgmentContext.Builder builder = JudgmentContext.builder().goal("Quality evaluation");
		if (quality != null) {
			builder.metadata(QualityJudge.QUALITY_ASSESSMENT, quality);
		}
		if (backport != null) {
			builder.metadata(QualityJudge.BACKPORT_ASSESSMENT, backport);
		}
		return builder.build();
	}

	@Nested
	class BothAssessmentsPresent {

		@Test
		void shouldPassWhenBothAssessmentsAreGood() {
			JudgmentContext ctx = contextWith(quality(JudgmentStatus.PASS, 0.9), backport(JudgmentStatus.PASS, 0.8));

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(judgment.pass()).isTrue();
			assertThat(judgment.checks()).allMatch(Check::passed);
		}

		@Test
		void shouldComputeWeightedScore() {
			JudgmentContext ctx = contextWith(quality(JudgmentStatus.PASS, 0.8), backport(JudgmentStatus.PASS, 0.6));

			Judgment judgment = judge.judge(ctx);

			// 0.8 * 0.7 + 0.6 * 0.3 = 0.56 + 0.18 = 0.74
			assertThat(judgment.reasoning()).contains("0.74");
		}

	}

	@Nested
	class MissingAssessments {

		@Test
		void shouldFailWhenQualityAssessmentMissing() {
			JudgmentContext ctx = contextWith(null, backport(JudgmentStatus.PASS, 0.8));

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(failedCheckNames(judgment)).contains("quality-present");
		}

		@Test
		void shouldFailWhenBackportAssessmentMissing() {
			JudgmentContext ctx = contextWith(quality(JudgmentStatus.PASS, 0.9), null);

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(failedCheckNames(judgment)).contains("backport-present");
		}

	}

	@Nested
	class ErrorAssessments {

		@Test
		void shouldFailWhenQualityAssessmentIsError() {
			JudgmentContext ctx = contextWith(quality(JudgmentStatus.ERROR, 0.0), backport(JudgmentStatus.PASS, 0.7));

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(failedCheckNames(judgment)).contains("quality-no-error");
		}

	}

	@Nested
	class ConsistencyChecks {

		@Test
		void shouldFlagContradictoryVerdicts() {
			JudgmentContext ctx = contextWith(quality(JudgmentStatus.FAIL, 0.3), backport(JudgmentStatus.PASS, 0.9));

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(failedCheckNames(judgment)).contains("consistency");
		}

		@Test
		void shouldNotFlagWhenBothFail() {
			JudgmentContext ctx = contextWith(quality(JudgmentStatus.FAIL, 0.3), backport(JudgmentStatus.FAIL, 0.2));

			Judgment judgment = judge.judge(ctx);

			// consistency check passes (both agree), but quality-no-error is not checked
			// since FAIL != ERROR
			assertThat(failedCheckNames(judgment)).doesNotContain("consistency");
		}

	}

	private static List<String> failedCheckNames(Judgment judgment) {
		return judgment.checks().stream().filter(c -> !c.passed()).map(Check::name).toList();
	}

}
