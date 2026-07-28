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
 * The served artifacts hold the contract's own discipline: the spec reads valid through
 * the engine's two-phase reader (already exercised at controller construction — made
 * explicit here), every dispatch site is CD-21-coherent against the catalog instance, and
 * a deployment missing an operation paints the {@code unresolved-operation} advisory (the
 * deployment-relative posture, DD-17).
 *
 * <p>
 * Both artifacts are generated — the spec from the v3 DSL, the catalog from the leaf
 * beans — so these are checks on what the emitter and the deployment produce, not on what
 * somebody typed. What they can still catch is the emitter and the deployment disagreeing
 * with each other, which is exactly what a hand-authored catalog used to hide.
 */
class V3ServingFixturesTest {

	private final WorkflowSpec spec = WorkflowV3AlphaController.readSpec();

	private final OperationCatalog catalog = WorkflowV3AlphaController.readCatalog();

	@Test
	void theServedSpecIsTheWholePipelineAndPassesBothValidationPhases() {
		assertThat(this.spec.metadata().name()).isEqualTo("pr-review");
		// The whole reviewed pipeline, not the linear slice the hand-authored resource
		// carried: the fix decision and its two arms, the build-health gate and the two
		// report arms behind it.
		assertThat(this.spec.nodes()).hasSize(19);
		assertThat(this.spec.nodes()).extracting(SpecNode::id)
			.contains("should-attempt-fix", "retest", "build-health", "assess", "assess-join", "published",
					"early-published");
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
		assertThat(rebase.id()).isEqualTo("rebase-on-main");
		assertThat(rebase.input()).containsEntry("prContext", new Binding("$node.fetch-pr-context.output"));
	}

	/**
	 * The live half of canvas-spike CS-6: what this deployment serves carries both the
	 * projector's {@code derived} block and, on every node, §8.2 {@code source} — which
	 * is what makes node-click&rarr;code answerable against a served document rather than
	 * against one assembled by hand.
	 */
	@Test
	void theServedViewCarriesBothProvenanceAndDerivation() {
		WorkflowView view = new WorkflowViewProjector().project(this.spec, this.catalog);

		assertThat(view.derived().labels()).isNotEmpty();
		assertThat(view.derived().ports()).isNotEmpty();
		assertThat(view.spec().nodes()).allSatisfy(node -> {
			assertThat(node.source()).as("node %s carries its declaration site", node.id()).isNotNull();
			assertThat(node.source().uri()).contains("PrReviewWorkflowV3.java");
		});
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

		// One advisory per *node*, not per operation: run-tests is placed twice, and the
		// second placement is a node a canvas has to paint just as unresolved as the
		// first.
		assertThat(view.diagnostics()).hasSize(2);
		assertThat(view.diagnostics()).extracting(Diagnostic::nodeId).containsExactlyInAnyOrder("run-tests", "retest");
		assertThat(view.diagnostics()).allSatisfy(diagnostic -> {
			assertThat(diagnostic.code()).isEqualTo(WorkflowViewProjector.UNRESOLVED_OPERATION);
			assertThat(diagnostic.severity()).isEqualTo(Diagnostic.Severity.WARNING);
			assertThat(diagnostic.phase()).isEqualTo(Diagnostic.Phase.ADVISORY);
			assertThat(diagnostic.message()).isEqualTo("Operation ref 'java:pr-review.run-tests:v1' does not"
					+ " resolve in catalog instance 'agentworks-pr-review-app@2026-07-22.0'.");
		});
		// never an invalid render (DD-17): the unresolved nodes still serve, untyped
		assertThat(view.derived().ports()).filteredOn(ports -> "run-tests".equals(ports.nodeId()))
			.singleElement()
			.satisfies(ports -> assertThat(ports.ports()).allSatisfy(port -> assertThat(port.schemaRef()).isNull()));
	}

}
