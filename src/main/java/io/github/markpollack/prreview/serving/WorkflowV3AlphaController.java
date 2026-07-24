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
 * The spec resource is hand-authored at Step 1.2 (the emitter speaks v3alpha at Stage
 * 2.1, whose exit criterion is reproducing this exact document) and passes the engine's
 * two-phase reader at startup — the served spec is valid by construction. Envelopes are
 * serialized with the contract-side Jackson 2 {@code WireJson} mapper (Boot 4's Jackson 3
 * never touches them), so the emission shape is exactly the producer schema's.
 */
@RestController
@RequestMapping(path = "/workflow/v3alpha", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkflowV3AlphaController {

	static final String SPEC_RESOURCE = "/v3alpha/workflow-pr-review-linear.json";

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
