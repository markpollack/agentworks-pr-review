package io.github.markpollack.prreview.steps;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.markpollack.prreview.model.AssessmentResult;
import org.springaicommunity.judge.result.JudgmentStatus;

/**
 * Parses structured JSON responses from AI assessments into {@link AssessmentResult}.
 *
 * <p>
 * Uses simple regex extraction rather than a JSON parser — the response format is
 * constrained and this avoids Jackson dependency in the parsing path. Handles malformed
 * responses gracefully by returning an ERROR result.
 */
final class AssessmentParser {

	private static final Pattern SCORE_PATTERN = Pattern.compile("\"score\"\\s*:\\s*(\\d+\\.?\\d*)");

	private static final Pattern STATUS_PATTERN = Pattern.compile("\"status\"\\s*:\\s*\"(PASS|FAIL)\"");

	private static final Pattern RATIONALE_PATTERN = Pattern.compile("\"rationale\"\\s*:\\s*\"([^\"]+)\"");

	private static final Pattern FINDINGS_PATTERN = Pattern.compile("\"findings\"\\s*:\\s*\\[([^\\]]*)]",
			Pattern.DOTALL);

	private static final Pattern FINDING_ITEM = Pattern.compile("\"([^\"]+)\"");

	private AssessmentParser() {
	}

	static AssessmentResult parse(String judgeName, String response) {
		if (response == null || response.isBlank()) {
			return new AssessmentResult(judgeName, JudgmentStatus.ERROR, 0.0, "Empty response from AI", List.of());
		}

		try {
			double score = extractDouble(SCORE_PATTERN, response, 0.0);
			JudgmentStatus status = extractStatus(response);
			String rationale = extractString(RATIONALE_PATTERN, response, "No rationale provided");
			List<String> findings = extractFindings(response);

			return new AssessmentResult(judgeName, status, score, rationale, findings);
		}
		catch (Exception ex) {
			return new AssessmentResult(judgeName, JudgmentStatus.ERROR, 0.0,
					"Failed to parse AI response: " + ex.getMessage(), List.of());
		}
	}

	private static double extractDouble(Pattern pattern, String text, double defaultValue) {
		Matcher m = pattern.matcher(text);
		if (m.find()) {
			return Double.parseDouble(m.group(1));
		}
		return defaultValue;
	}

	private static JudgmentStatus extractStatus(String text) {
		Matcher m = STATUS_PATTERN.matcher(text);
		if (m.find()) {
			return "PASS".equals(m.group(1)) ? JudgmentStatus.PASS : JudgmentStatus.FAIL;
		}
		return JudgmentStatus.ABSTAIN;
	}

	private static String extractString(Pattern pattern, String text, String defaultValue) {
		Matcher m = pattern.matcher(text);
		if (m.find()) {
			return m.group(1);
		}
		return defaultValue;
	}

	private static List<String> extractFindings(String text) {
		Matcher arrayMatcher = FINDINGS_PATTERN.matcher(text);
		if (!arrayMatcher.find()) {
			return List.of();
		}
		String arrayContent = arrayMatcher.group(1);
		List<String> findings = new ArrayList<>();
		Matcher itemMatcher = FINDING_ITEM.matcher(arrayContent);
		while (itemMatcher.find()) {
			findings.add(itemMatcher.group(1));
		}
		return findings;
	}

}
