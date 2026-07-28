package io.github.markpollack.prreview.model;

/**
 * What the test run is dispatched with. Nothing produces one of these — you assemble one
 * at the dispatch site — so its components <em>are</em> the CD-21 named parameters.
 *
 * <p>
 * The v1 step took only the {@link ConflictReport} and fished the {@link PrContext} out
 * of the ambient context to discover affected modules. Both values are inputs; only one
 * of them was declared.
 *
 * @param conflicts the conflict analysis that decides whether tests run at all
 * @param context the PR whose changed files select the modules under test
 */
public record TestRequest(ConflictReport conflicts, PrContext context) {
}
