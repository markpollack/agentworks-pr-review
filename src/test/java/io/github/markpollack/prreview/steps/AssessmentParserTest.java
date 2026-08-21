package io.github.markpollack.prreview.steps;

import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.Finding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentParserTest {

	@Nested
	class ValidResponses {

		@Test
		void shouldParseStructuredFindings() {
			String json = """
					{
					  "score": 0.35,
					  "status": "FAIL",
					  "rationale": "Serializing notifications is the right fix, but closeGracefully drops queued work.",
					  "findings": [
					    {
					      "severity": "BLOCKING",
					      "file": "acp-core/src/main/java/com/agentclientprotocol/sdk/spec/AcpClientSession.java",
					      "symbol": "AcpClientSession.closeGracefully",
					      "line": 375,
					      "claim": "Queued notifications are discarded on graceful close.",
					      "evidence": "tryEmitComplete() is followed synchronously by subscription.dispose().",
					      "correction": "Await the drain before disposing."
					    }
					  ]
					}
					""";

			AssessmentResult result = AssessmentParser.parse("code-quality", json);

			assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(result.findings()).hasSize(1);

			Finding finding = result.findings().get(0);
			assertThat(finding.normalizedSeverity()).isEqualTo(Finding.BLOCKING);
			assertThat(finding.symbol()).isEqualTo("AcpClientSession.closeGracefully");
			assertThat(finding.evidence()).contains("dispose()");
			assertThat(finding.correction()).isNotBlank();
			assertThat(result.hasBlocking()).isTrue();
		}

		@Test
		@DisplayName("a rationale containing quoted symbols survives — the old regex truncated here")
		void shouldParseRationaleContainingQuotes() {
			String json = """
					{
					  "score": 0.9,
					  "status": "PASS",
					  "rationale": "The \\"concatMap\\" pipeline preserves ordering as claimed.",
					  "findings": []
					}
					""";

			AssessmentResult result = AssessmentParser.parse("code-quality", json);

			assertThat(result.rationale()).isEqualTo("The \"concatMap\" pipeline preserves ordering as claimed.");
			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		}

		@Test
		void shouldHandleEmptyFindings() {
			String json = """
					{ "score": 1.0, "status": "PASS", "rationale": "Sound change.", "findings": [] }
					""";

			assertThat(AssessmentParser.parse("code-quality", json).findings()).isEmpty();
		}

		@Test
		@DisplayName("a bare-string finding is kept, unanchored, rather than silently dropped")
		void shouldTolerateAModelThatIgnoresTheSchema() {
			String json = """
					{ "score": 0.6, "status": "FAIL", "rationale": "Some concerns.",
					  "findings": ["Unused import on line 12"] }
					""";

			AssessmentResult result = AssessmentParser.parse("code-quality", json);

			// Weak evidence, but discarding it would make the reviewer look like it found
			// nothing — which is a different and much more misleading failure.
			assertThat(result.findings()).hasSize(1);
			assertThat(result.findings().get(0).claim()).isEqualTo("Unused import on line 12");
			assertThat(result.findings().get(0).normalizedSeverity()).isEqualTo(Finding.MINOR);
		}

		@Test
		@DisplayName("when the agent narrates in conforming objects, the last one is the verdict")
		void shouldTakeTheLastObjectWhenSeveralAreEmitted() {
			String json = """
					{ "score": 0.0, "status": "FAIL", "rationale": "Reading the diff.", "findings": [] }
					{ "score": 0.0, "status": "FAIL", "rationale": "Reading the callers.", "findings": [] }
					{ "score": 0.4, "status": "FAIL", "rationale": "Found a real problem.",
					  "findings": [{"severity": "BLOCKING", "file": "A.java", "symbol": "A.b",
					                "claim": "c", "evidence": "e", "correction": "f"}] }
					""";

			AssessmentResult result = AssessmentParser.parse("code-quality", json);

			assertThat(result.rationale()).isEqualTo("Found a real problem.");
			assertThat(result.findings()).hasSize(1);
		}

		@Test
		void shouldClampAnOutOfRangeScore() {
			String json = """
					{ "score": 4.2, "status": "PASS", "rationale": "Enthusiastic.", "findings": [] }
					""";

			assertThat(AssessmentParser.parse("code-quality", json).score()).isEqualTo(1.0);
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
			assertThat(AssessmentParser.parse("test", "   ").status()).isEqualTo(JudgmentStatus.ERROR);
		}

		@Test
		void shouldReturnErrorWhenThereIsNoJsonAtAll() {
			AssessmentResult result = AssessmentParser.parse("test", "I could not access the repository.");

			assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
			assertThat(result.rationale()).contains("No JSON object");
		}

		@Test
		void shouldUseDefaultsForMissingFields() {
			AssessmentResult result = AssessmentParser.parse("test", """
					{ "score": 0.7 }
					""");

			assertThat(result.score()).isEqualTo(0.7);
			assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
			assertThat(result.rationale()).isEqualTo("No rationale provided");
			assertThat(result.findings()).isEmpty();
		}

	}

}
