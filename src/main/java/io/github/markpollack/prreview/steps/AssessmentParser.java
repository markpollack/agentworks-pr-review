package io.github.markpollack.prreview.steps;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.markpollack.judge.result.JudgmentStatus;
import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.Finding;

/**
 * Parses structured JSON responses from AI assessments into {@link AssessmentResult}.
 *
 * <p>
 * This used to extract five fields with regexes, on the reasoning that it avoided a
 * Jackson dependency — but the application already carries Jackson through
 * {@code spring-boot-starter-web}, so the dependency was never actually avoided, and the
 * regexes cost real correctness:
 *
 * <ul>
 * <li>{@code "rationale"\s*:\s*"([^"]+)"} breaks on the first embedded quote. A rationale
 * naming a symbol in quotes — which is what a good rationale does — truncates or fails to
 * match.</li>
 * <li>The findings array matcher stopped at the first {@code ]}, and each item was any
 * quoted run of characters. Structured findings would have shredded into their own JSON
 * keys.</li>
 * </ul>
 *
 * <p>
 * That second limitation was shaping the prompt: findings had to be flat strings, which
 * meant no severity, no file, no separable evidence — and therefore nothing a judge could
 * score. The parser was quietly setting the ceiling on what the assessment step could be
 * evaluated for.
 *
 * <p>
 * A malformed response still yields an ERROR result rather than an exception.
 */
final class AssessmentParser {

	private static final ObjectMapper MAPPER = new ObjectMapper()
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private AssessmentParser() {
	}

	static AssessmentResult parse(String judgeName, String response) {
		if (response == null || response.isBlank()) {
			return error(judgeName, "Empty response from AI");
		}
		Optional<String> json = lastJsonObject(response);
		if (json.isEmpty()) {
			return error(judgeName, "No JSON object found in AI response");
		}
		try {
			JsonNode root = MAPPER.readTree(json.get());
			return new AssessmentResult(judgeName, status(root), score(root),
					root.path("rationale").asText("No rationale provided"), findings(root));
		}
		catch (Exception ex) {
			return error(judgeName, "Failed to parse AI response: " + ex.getMessage());
		}
	}

	private static AssessmentResult error(String judgeName, String reason) {
		return new AssessmentResult(judgeName, JudgmentStatus.ERROR, 0.0, reason, List.of());
	}

	private static double score(JsonNode root) {
		double score = root.path("score").asDouble(0.0);
		return Math.max(0.0, Math.min(1.0, score));
	}

	private static JudgmentStatus status(JsonNode root) {
		String status = root.path("status").asText("");
		return switch (status.trim().toUpperCase(java.util.Locale.ROOT)) {
			case "PASS" -> JudgmentStatus.PASS;
			case "FAIL" -> JudgmentStatus.FAIL;
			default -> JudgmentStatus.ABSTAIN;
		};
	}

	private static List<Finding> findings(JsonNode root) {
		JsonNode array = root.path("findings");
		if (!array.isArray()) {
			return List.of();
		}
		List<Finding> findings = new ArrayList<>();
		for (JsonNode node : array) {
			// A model that ignores the schema and emits a bare string still contributes
			// its
			// text rather than being dropped — an unanchored finding is weak evidence,
			// but
			// silently discarding it would look like the reviewer found nothing.
			if (node.isTextual()) {
				findings.add(new Finding(null, null, null, 0, node.asText(), null, null));
				continue;
			}
			findings.add(MAPPER.convertValue(node, Finding.class));
		}
		return findings;
	}

	/**
	 * The last balanced top-level JSON object in the response.
	 *
	 * <p>
	 * Last, not first. An agentic CLI under an output contract may emit a conforming
	 * object at each step of its turn — a progress note rather than a verdict — with the
	 * real answer last. Taking the first object scores such a run as having found
	 * nothing, and because every object is well-formed, nothing appears to be wrong.
	 */
	static Optional<String> lastJsonObject(String raw) {
		int depth = 0;
		int start = -1;
		boolean inString = false;
		boolean escaped = false;
		String last = null;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (inString) {
				if (escaped) {
					escaped = false;
				}
				else if (c == '\\') {
					escaped = true;
				}
				else if (c == '"') {
					inString = false;
				}
				continue;
			}
			switch (c) {
				case '"' -> inString = true;
				case '{' -> {
					if (depth == 0) {
						start = i;
					}
					depth++;
				}
				case '}' -> {
					if (depth > 0) {
						depth--;
						if (depth == 0 && start >= 0) {
							last = raw.substring(start, i + 1);
							start = -1;
						}
					}
				}
				default -> {
				}
			}
		}
		return Optional.ofNullable(last);
	}

}
