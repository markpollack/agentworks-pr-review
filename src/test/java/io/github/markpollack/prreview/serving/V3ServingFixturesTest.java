package io.github.markpollack.prreview.serving;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.markpollack.workflow.spec.v3.Binding;
import io.github.markpollack.workflow.spec.v3.Diagnostic;
import io.github.markpollack.workflow.spec.v3.OperationSpecNode;
import io.github.markpollack.workflow.spec.v3.SpecNode;
import io.github.markpollack.workflow.spec.v3.WorkflowSpec;
import io.github.markpollack.workflow.spec.v3.envelope.CatalogEntry;
import io.github.markpollack.workflow.spec.v3.envelope.OperationCatalog;
import io.github.markpollack.workflow.spec.v3.envelope.WorkflowView;
import io.github.markpollack.workflow.spec.v3.view.WorkflowViewProjector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hand-authored serving fixtures hold the contract's own discipline: the spec reads
 * valid through the engine's two-phase reader (already exercised at controller
 * construction — made explicit here), every dispatch site is CD-21-coherent against the
 * catalog instance, and a deployment missing an operation paints the pinned
 * {@code unresolved-operation} advisory (the deployment-relative posture, DD-17).
 */
class V3ServingFixturesTest {

	private final WorkflowSpec spec = WorkflowV3AlphaController.readSpec();

	private final OperationCatalog catalog = WorkflowV3AlphaController.readCatalog();

	@Test
	void sliceSpecPassesBothValidationPhases() {
		assertThat(this.spec.metadata().name()).isEqualTo("pr-review");
		assertThat(this.spec.nodes()).hasSize(7);
	}

	@Test
	void everyDispatchSiteIsConstructibleAgainstTheCatalog() {
		// CONTRACT §7.4/CD-21: the constructed input is exactly the binding-map keys —
		// required members covered; within declared members when the schema is closed
		Map<String, CatalogEntry> byRef = new TreeMap<>();
		this.catalog.operations().forEach(entry -> byRef.put(entry.ref(), entry));
		for (SpecNode node : this.spec.nodes()) {
			if (!(node instanceof OperationSpecNode operation)) {
				continue;
			}
			CatalogEntry entry = byRef.get(this.spec.operations().get(operation.operation()).ref());
			assertThat(entry).as("node %s resolves in the instance", node.id()).isNotNull();
			JsonNode schema = entry.input().schema();
			Set<String> keys = operation.input() == null ? Set.of() : operation.input().keySet();
			Set<String> required = new HashSet<>();
			schema.get("required").forEach(name -> required.add(name.asText()));
			Set<String> properties = new HashSet<>();
			schema.get("properties").fieldNames().forEachRemaining(properties::add);
			assertThat(keys).as("node %s covers the required members (CD-21)", node.id()).containsAll(required);
			assertThat(properties).as("node %s stays within the declared members (CD-21)", node.id()).containsAll(keys);
		}
	}

	@Test
	void bindingsChainTheRealStepOutputs() {
		OperationSpecNode rebase = (OperationSpecNode) this.spec.nodes().get(1);
		assertThat(rebase.input()).containsEntry("context", new Binding("$node.fetch-pr-context.output"));
	}

	@Test
	void deploymentMissingAnOperationPaintsTheUnresolvedAdvisory() {
		List<CatalogEntry> withoutRunTests = this.catalog.operations()
			.stream()
			.filter(entry -> !"java:pr-review.run-tests:v1".equals(entry.ref()))
			.toList();
		OperationCatalog stripped = new OperationCatalog(this.catalog.apiVersion(), this.catalog.kind(),
				new OperationCatalog.CatalogInstance("agentworks-pr-review-app@2026-07-22.0", null), withoutRunTests);

		WorkflowView view = new WorkflowViewProjector().project(this.spec, stripped);

		assertThat(view.diagnostics()).singleElement().satisfies(diagnostic -> {
			assertThat(diagnostic.code()).isEqualTo(WorkflowViewProjector.UNRESOLVED_OPERATION);
			assertThat(diagnostic.severity()).isEqualTo(Diagnostic.Severity.WARNING);
			assertThat(diagnostic.phase()).isEqualTo(Diagnostic.Phase.ADVISORY);
			assertThat(diagnostic.nodeId()).isEqualTo("run-tests");
			assertThat(diagnostic.message()).isEqualTo("Operation ref 'java:pr-review.run-tests:v1' does not"
					+ " resolve in catalog instance 'agentworks-pr-review-app@2026-07-22.0'.");
		});
		// never an invalid render (DD-17): the unresolved node still serves, untyped
		assertThat(view.derived().ports()).filteredOn(ports -> "run-tests".equals(ports.nodeId()))
			.singleElement()
			.satisfies(ports -> assertThat(ports.ports()).allSatisfy(port -> assertThat(port.schemaRef()).isNull()));
	}

}
