package io.github.markpollack.prreview.model;

import java.time.Instant;

/**
 * A comment on a pull request.
 *
 * @param author GitHub login of the comment author
 * @param body comment text
 * @param createdAt when the comment was posted
 */
public record Comment(String author, String body, Instant createdAt) {
}
