package io.github.markpollack.prreview.model;

import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * What the T1 version-pattern check found. Not a gate: a FAIL records the verdict and the
 * pipeline continues.
 *
 * @param status the judge's verdict
 * @param reasoning the judge's explanation
 */
public record VersionPatternFinding(JudgmentStatus status, String reasoning) {
}
