package io.github.markpollack.prreview.model;

import io.github.markpollack.workflow.spec.v3.envelope.VerdictRecord;

/**
 * What the full report is assembled from, on the arm where the build-health gate passed.
 *
 * <p>
 * In v1 one {@code AssembleReportStep} read eight context keys and served both arms. The
 * arms dispatch different values — only this one has a quality verdict — so the types
 * split what the ambient context hid.
 *
 * @param context the PR under review
 * @param rebase the rebase outcome
 * @param conflicts the conflict analysis
 * @param build the build the gate passed on
 * @param quality the T2 meta-judge's verdict
 * @param verdict the gate's own verdict record (CD-9)
 */
public record FullReportRequest(PrContext context, RebaseResult rebase, ConflictReport conflicts, BuildResult build,
		QualityVerdict quality, VerdictRecord verdict) {
}
