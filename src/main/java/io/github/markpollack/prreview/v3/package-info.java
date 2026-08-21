/**
 * This application's workflow as the {@code workflow/v3alpha} contract carries it —
 * authored in the v3 DSL, emitted as a governed artifact, and served over the v3alpha
 * seam.
 *
 * <h2>Emission is a build-time act, not a runtime one</h2> The spec is emitted from
 * {@link io.github.markpollack.prreview.v3.PrReviewWorkflowV3} and committed as
 * {@code src/main/resources/v3alpha/workflow-pr-review.json}; the served document is that
 * resource. Repository-relative node locations are emitted beside it in a
 * {@code WorkflowArtifactRecord}, so source navigation remains truthful without entering
 * the producer-neutral workflow or its approval identity.
 *
 * <p>
 * {@code PrReviewSpecV3Test} pins the committed resources against what the DSL emits now,
 * so the code and the artifact cannot drift; regenerate deliberately with
 * {@code -Dv3.spec.regenerate=true} and read the diff as a contract diff.
 */
package io.github.markpollack.prreview.v3;
