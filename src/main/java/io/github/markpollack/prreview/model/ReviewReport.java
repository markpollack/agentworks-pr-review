package io.github.markpollack.prreview.model;

import java.time.Instant;
import java.util.List;

import org.springaicommunity.judge.result.Judgment;

/**
 * Final PR review report assembled from all pipeline phases.
 *
 * @param prContext full PR context
 * @param rebaseResult rebase outcome
 * @param conflictReport conflict analysis
 * @param buildResult build/test outcome
 * @param assessments all judge/assessment results
 * @param judgments raw judge verdicts (from CascadedJury)
 * @param generatedAt report generation timestamp
 */
public record ReviewReport(PrContext prContext, RebaseResult rebaseResult, ConflictReport conflictReport,
		BuildResult buildResult, List<AssessmentResult> assessments, List<Judgment> judgments, Instant generatedAt) {

	public ReviewReport {
		assessments = List.copyOf(assessments);
		judgments = List.copyOf(judgments);
	}

	public static ReviewReport error(String message) {
		PrContext emptyContext = new PrContext(0, "Pipeline Error", message, "unknown", List.of(), "error", "main",
				"unknown", List.of(), List.of(), List.of(), List.of());
		return new ReviewReport(emptyContext, new RebaseResult(false, "unknown", List.of(), message),
				ConflictReport.clean(), BuildResult.skippedBuild(), List.of(), List.of(), Instant.now());
	}

}
