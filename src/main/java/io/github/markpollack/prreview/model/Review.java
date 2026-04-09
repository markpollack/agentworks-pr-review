package io.github.markpollack.prreview.model;

import java.time.Instant;

/**
 * A review on a pull request.
 *
 * @param author GitHub login of the reviewer
 * @param state one of: APPROVED, CHANGES_REQUESTED, COMMENTED, DISMISSED
 * @param body review body text
 * @param submittedAt when the review was submitted
 */
public record Review(String author, String state, String body, Instant submittedAt) {
}
