package io.github.markpollack.prreview.model;

/**
 * What the AI fix attempt is dispatched with — the §7.4 named-wrapper idiom.
 *
 * @param build the failing build the fix is asked to repair
 * @param context the PR being fixed
 */
public record FixRequest(BuildResult build, PrContext context) {
}
