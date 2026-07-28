package io.github.markpollack.prreview.model;

/**
 * What branch cleanup produced. The v1 step was a {@code Step<Object,Object>} that
 * returned its input untouched — a node whose output said nothing about what it did.
 *
 * @param branch the review branch that was cleaned up
 * @param cleaned whether cleanup succeeded
 */
public record CleanupResult(String branch, boolean cleaned) {
}
