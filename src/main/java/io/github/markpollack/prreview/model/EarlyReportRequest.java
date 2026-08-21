package io.github.markpollack.prreview.model;

import io.github.markpollack.workflow.spec.v3.envelope.GateEvaluation;

/**
 * What the early report is assembled from, on the arm where the build-health gate failed:
 * everything the full report has except the assessments that never ran.
 *
 * @param context the PR under review
 * @param rebase the rebase outcome
 * @param conflicts the conflict analysis
 * @param build the build the gate failed on
 * @param verdict the gate's own evaluation (CD-9)
 */
public record EarlyReportRequest(PrContext context, RebaseResult rebase, ConflictReport conflicts, BuildResult build,
		GateEvaluation verdict) {
}
