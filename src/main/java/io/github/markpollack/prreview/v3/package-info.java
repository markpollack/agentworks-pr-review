/**
 * This application's workflow as the {@code workflow/v3alpha} contract carries it —
 * authored in the v3 DSL, emitted as a governed artifact, and served over the v3alpha
 * seam.
 *
 * <h2>Emission is a build-time act, not a runtime one</h2> The spec is emitted from
 * {@link io.github.markpollack.prreview.v3.PrReviewWorkflowV3} and committed as
 * {@code src/main/resources/v3alpha/workflow-pr-review.json}; the served document is that
 * resource. It is deliberately not emitted on startup, and the reason is CONTRACT §8.2:
 * each node's {@code source.uri} is repository-relative, which the emitter can only
 * produce inside a checkout. A deployment is not a checkout, so a spec emitted in
 * production would silently carry no provenance at all — a degradation §8.2 permits and
 * nobody would notice. Emitting in the build and serving the artifact puts the provenance
 * where the contract wants it and leaves the deployment doing what canvas-spike CS-8 says
 * is its job: knowing which repository it served the document from.
 *
 * <p>
 * {@code PrReviewV3ConfigTest} pins the committed resource against what the DSL emits
 * now, so the code and the artifact cannot drift; regenerate deliberately with
 * {@code -Dv3.spec.regenerate=true} and read the diff as a contract diff.
 */
package io.github.markpollack.prreview.v3;
