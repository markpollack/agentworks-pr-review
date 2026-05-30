package io.github.markpollack.prreview.model;

import java.util.List;

import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * Result of a single AI assessment (code quality, backport, version patterns).
 *
 * @param judgeName which assessment produced this
 * @param status PASS/FAIL/ABSTAIN/ERROR (from agent-judge-core)
 * @param score 0.0–1.0 normalized score
 * @param rationale explanation
 * @param findings specific findings/issues
 */
public record AssessmentResult(String judgeName, JudgmentStatus status, double score, String rationale,
		List<String> findings) {

	public AssessmentResult {
		findings = List.copyOf(findings);
	}

}
