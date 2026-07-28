package io.github.markpollack.prreview.model;

/**
 * What a run is submitted with — the pipeline's {@code $input} (CONTRACT CD-20).
 *
 * <p>
 * The v1 pipeline's entrypoint took a bare {@code Integer}, which is a shape no
 * {@code inputSchema} can name a field of. §7.4 has no whole-value form, so even one
 * value needs a name to sit under, and the component supplies it: {@code {"pr": 5774}}.
 *
 * @param pr the pull-request number to review
 */
public record PrRequest(int pr) {
}
