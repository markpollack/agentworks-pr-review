package io.github.markpollack.prreview.serving;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import io.github.markpollack.workflow.spec.v3.DefaultWorkflowSpecReader;
import io.github.markpollack.workflow.spec.v3.ContentDigest;
import io.github.markpollack.workflow.spec.v3.WireJson;
import io.github.markpollack.workflow.spec.v3.WorkflowSpec;
import io.github.markpollack.workflow.spec.v3.envelope.CuratedCatalog;
import io.github.markpollack.workflow.spec.v3.envelope.WorkflowArtifactRecord;
import io.github.markpollack.workflow.spec.v3.envelope.WorkflowCatalogSelection;
import io.github.markpollack.workflow.spec.v3.envelope.WorkflowView;
import io.github.markpollack.workflow.spec.v3.view.WorkflowViewProjector;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The v3alpha serving seam (CONTRACT DD-18; ROADMAP Step 1.2): serves the engine's read
 * model for this app's ONE workflow, its mutable catalog, immutable approved selection,
 * and independent source-evidence record. The wire shapes are the contract; HTTP is
 * host-app detail. No list-all endpoint: the collection surface stays deferred.
 *
 * <p>
 * All four resources are <b>generated</b>, not hand-authored: the workflow and source
 * evidence come from one DSL emission; the catalog and selection derive from that
 * workflow and the provider beans ({@code PrReviewSpecV3Test}). The view verifies and
 * joins the exact evidence hash without putting source locations into execution identity.
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

	static final String CATALOG_RESOURCE = "/v3alpha/curated-catalog.json";

	static final String SELECTION_RESOURCE = "/v3alpha/workflow-catalog-selection.json";

	static final String ARTIFACT_RECORD_RESOURCE = "/v3alpha/workflow-artifact-record.json";

	private final String viewJson;

	private final String catalogJson;

	private final String selectionJson;

	private final String artifactRecordJson;

	public WorkflowV3AlphaController() {
		WorkflowSpec spec = readSpec();
		CuratedCatalog catalog = readCatalog();
		WorkflowCatalogSelection selection = readSelection();
		WorkflowArtifactRecord artifactRecord = readArtifactRecord();
		WorkflowView view = new WorkflowViewProjector().project(spec, selection, ContentDigest.sha256(artifactRecord),
				artifactRecord);
		try {
			this.viewJson = WireJson.mapper().writeValueAsString(view);
			this.catalogJson = WireJson.mapper().writeValueAsString(catalog);
			this.selectionJson = WireJson.mapper().writeValueAsString(selection);
			this.artifactRecordJson = WireJson.mapper().writeValueAsString(artifactRecord);
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

	@GetMapping("/selection")
	public String selection() {
		return this.selectionJson;
	}

	@GetMapping("/artifact-record")
	public String artifactRecord() {
		return this.artifactRecordJson;
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

	static CuratedCatalog readCatalog() {
		try (InputStream in = WorkflowV3AlphaController.class.getResourceAsStream(CATALOG_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("missing resource " + CATALOG_RESOURCE);
			}
			return WireJson.mapper().readValue(in, CuratedCatalog.class);
		}
		catch (IOException e) {
			throw new UncheckedIOException("failed reading " + CATALOG_RESOURCE, e);
		}
	}

	static WorkflowCatalogSelection readSelection() {
		return readEnvelope(SELECTION_RESOURCE, WorkflowCatalogSelection.class);
	}

	static WorkflowArtifactRecord readArtifactRecord() {
		return readEnvelope(ARTIFACT_RECORD_RESOURCE, WorkflowArtifactRecord.class);
	}

	private static <T> T readEnvelope(String resource, Class<T> type) {
		try (InputStream in = WorkflowV3AlphaController.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("missing resource " + resource);
			}
			return WireJson.mapper().readValue(in, type);
		}
		catch (IOException e) {
			throw new UncheckedIOException("failed reading " + resource, e);
		}
	}

}
