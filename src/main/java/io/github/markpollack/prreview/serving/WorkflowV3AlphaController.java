package io.github.markpollack.prreview.serving;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import io.github.markpollack.workflow.spec.v3.DefaultWorkflowSpecReader;
import io.github.markpollack.workflow.spec.v3.WireJson;
import io.github.markpollack.workflow.spec.v3.WorkflowSpec;
import io.github.markpollack.workflow.spec.v3.envelope.OperationCatalog;
import io.github.markpollack.workflow.spec.v3.envelope.WorkflowView;
import io.github.markpollack.workflow.spec.v3.view.WorkflowViewProjector;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The v3alpha serving seam (CONTRACT DD-18; ROADMAP Step 1.2): serves the engine's read
 * model for this app's ONE workflow — the {@code WorkflowView} of the pr-review emittable
 * linear slice and the deployment's catalog instance. The wire shapes are the contract;
 * HTTP is host-app detail. No list-all endpoint: the collection surface stays deferred.
 *
 * <p>
 * Both resources are <b>generated</b>, not hand-authored: the spec is emitted from
 * {@code io.github.markpollack.prreview.v3.PrReviewWorkflowV3} and the catalog is derived
 * from the leaf beans this deployment holds, both in the build
 * ({@code PrReviewSpecV3Test}). They are read back as resources rather than taken from
 * the {@code WorkflowSpec} and {@code OperationCatalog} beans for one reason, and it is a
 * contract reason: each node's §8.2 {@code source.uri} is repository-relative, and only
 * an emitter running inside a checkout can produce one. A deployment is not a checkout,
 * so a spec emitted at startup would serve a canvas no way back to the code — silently,
 * because §8.2 makes absence the legal answer. Serving what the build emitted keeps the
 * provenance and leaves resolution where canvas-spike CS-8 put it: with the deployment
 * that knows which repository it served the document from.
 *
 * <p>
 * The spec passes the engine's two-phase reader at startup, so the served document is
 * valid by construction. Envelopes are serialized with the contract-side Jackson 2
 * {@code WireJson} mapper (Boot 4's Jackson 3 never touches them), so the emission shape
 * is exactly the producer schema's.
 */
@RestController
@RequestMapping(path = "/workflow/v3alpha", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkflowV3AlphaController {

	static final String SPEC_RESOURCE = "/v3alpha/workflow-pr-review.json";

	static final String CATALOG_RESOURCE = "/v3alpha/operation-catalog.json";

	private final String viewJson;

	private final String catalogJson;

	public WorkflowV3AlphaController() {
		WorkflowSpec spec = readSpec();
		OperationCatalog catalog = readCatalog();
		WorkflowView view = new WorkflowViewProjector().project(spec, catalog);
		try {
			this.viewJson = WireJson.mapper().writeValueAsString(view);
			this.catalogJson = WireJson.mapper().writeValueAsString(catalog);
		}
		catch (IOException e) {
			throw new UncheckedIOException("v3alpha envelopes are not serializable", e);
		}
	}

	@GetMapping("/view")
	public String view() {
		return this.viewJson;
	}

	@GetMapping("/catalog")
	public String catalog() {
		return this.catalogJson;
	}

	static WorkflowSpec readSpec() {
		try (InputStream in = WorkflowV3AlphaController.class.getResourceAsStream(SPEC_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("missing resource " + SPEC_RESOURCE);
			}
			return new DefaultWorkflowSpecReader().read(in);
		}
		catch (IOException e) {
			throw new UncheckedIOException("failed reading " + SPEC_RESOURCE, e);
		}
	}

	static OperationCatalog readCatalog() {
		try (InputStream in = WorkflowV3AlphaController.class.getResourceAsStream(CATALOG_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("missing resource " + CATALOG_RESOURCE);
			}
			return WireJson.mapper().readValue(in, OperationCatalog.class);
		}
		catch (IOException e) {
			throw new UncheckedIOException("failed reading " + CATALOG_RESOURCE, e);
		}
	}

}
