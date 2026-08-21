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
 * @param findings the defects claimed, each anchored and evidenced — see {@link Finding}
 */
public record AssessmentResult(String judgeName, JudgmentStatus status, double score, String rationale,
		List<Finding> findings) {

	public AssessmentResult {
		findings = List.copyOf(findings);
	}

	/** Findings of one severity, most useful for weighting a report or a judge. */
	public List<Finding> findingsOfSeverity(String severity) {
		return this.findings.stream().filter(f -> f.normalizedSeverity().equals(severity)).toList();
	}

	public boolean hasBlocking() {
		return !findingsOfSeverity(Finding.BLOCKING).isEmpty();
	}

}
