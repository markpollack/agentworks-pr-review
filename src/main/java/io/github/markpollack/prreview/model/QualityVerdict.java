package io.github.markpollack.prreview.model;

import io.github.markpollack.judge.result.JudgmentStatus;

/**
 * What the T2 quality meta-judge concluded about the AI assessments.
 *
 * <p>
 * It is a type of its own rather than a second {@link VersionPatternFinding} because the
 * graph binds by type: two judges producing one type would make every downstream
 * parameter of that type ambiguous, and the ambiguity would have to be resolved by hand.
 *
 * @param status the judge's verdict
 * @param reasoning the judge's explanation
 */
public record QualityVerdict(JudgmentStatus status, String reasoning) {
}
