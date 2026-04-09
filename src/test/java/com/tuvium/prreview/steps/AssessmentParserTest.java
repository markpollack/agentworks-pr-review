package com.tuvium.prreview.steps;

import com.tuvium.prreview.model.AssessmentResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springaicommunity.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentParserTest {

	@Nested
	class ValidResponses {

		@Test
		void shouldParseCompleteJsonResponse() {
			String json = """
					{
					  "score": 0.85,
					  "status": "PASS",
					  "rationale": "Clean implementation with good test coverage",
					  "findings": ["Minor: unused import in line 12", "Consider using Optional"]
					}
					""";

			AssessmentResult result = AssessmentParser.parse("code-quality", json);

			assertThat(result.judgeName()).isEqualTo("code-quality");
			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(result.score()).isEqualTo(0.85);
			assertThat(result.rationale()).isEqualTo("Clean implementation with good test coverage");
			assertThat(result.findings()).containsExactly("Minor: unused import in line 12", "Consider using Optional");
		}

		@Test
		void shouldParseFailStatus() {
			String json = """
					{
					  "score": 0.4,
					  "status": "FAIL",
					  "rationale": "Missing tests for error handling",
					  "findings": ["No tests for null input"]
					}
					""";

			AssessmentResult result = AssessmentParser.parse("backport", json);

			assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(result.score()).isEqualTo(0.4);
		}

		@Test
		void shouldHandleEmptyFindings() {
			String json = """
					{
					  "score": 1.0,
					  "status": "PASS",
					  "rationale": "Perfect",
					  "findings": []
					}
					""";

			AssessmentResult result = AssessmentParser.parse("code-quality", json);

			assertThat(result.findings()).isEmpty();
		}

	}

	@Nested
	class MalformedResponses {

		@Test
		void shouldReturnErrorForNullResponse() {
			AssessmentResult result = AssessmentParser.parse("test", null);

			assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(result.rationale()).contains("Empty response");
		}

		@Test
		void shouldReturnErrorForBlankResponse() {
			AssessmentResult result = AssessmentParser.parse("test", "   ");

			assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
		}

		@Test
		void shouldUseDefaultsForMissingFields() {
			String json = """
					{ "score": 0.7 }
					""";

			AssessmentResult result = AssessmentParser.parse("test", json);

			assertThat(result.score()).isEqualTo(0.7);
			assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(result.rationale()).isEqualTo("No rationale provided");
			assertThat(result.findings()).isEmpty();
		}

	}

}
