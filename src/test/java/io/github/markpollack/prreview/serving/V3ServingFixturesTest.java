package io.github.markpollack.prreview.serving;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.markpollack.workflow.spec.v3.Binding;
import io.github.markpollack.workflow.spec.v3.ContentDigest;
import io.github.markpollack.workflow.spec.v3.OperationSpecNode;
import io.github.markpollack.workflow.spec.v3.ProviderContractRef;
import io.github.markpollack.workflow.spec.v3.SpecNode;
import io.github.markpollack.workflow.spec.v3.WorkflowSpec;
import io.github.markpollack.workflow.spec.v3.catalog.CatalogSelectionException;
import io.github.markpollack.workflow.spec.v3.catalog.WorkflowCatalogSelector;
import io.github.markpollack.workflow.spec.v3.envelope.CuratedCatalog;
import io.github.markpollack.workflow.spec.v3.envelope.ProviderContract;
import io.github.markpollack.workflow.spec.v3.envelope.SelectedProvider;
import io.github.markpollack.workflow.spec.v3.envelope.WorkflowArtifactRecord;
import io.github.markpollack.workflow.spec.v3.envelope.WorkflowCatalogSelection;
import io.github.markpollack.workflow.spec.v3.envelope.WorkflowView;
import io.github.markpollack.workflow.spec.v3.view.WorkflowViewProjector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Coherence checks across the four generated v3alpha artifacts this app serves. */
class V3ServingFixturesTest {

	private final WorkflowSpec spec = WorkflowV3AlphaController.readSpec();

	private final CuratedCatalog catalog = WorkflowV3AlphaController.readCatalog();

	private final WorkflowCatalogSelection selection = WorkflowV3AlphaController.readSelection();

	private final WorkflowArtifactRecord artifactRecord = WorkflowV3AlphaController.readArtifactRecord();

	@Test
	void theServedSpecIsTheWholePipelineAndPassesBothValidationPhases() {
		assertThat(this.spec.metadata().name()).isEqualTo("pr-review");
		assertThat(this.spec.nodes()).hasSize(19);
		assertThat(this.spec.nodes()).extracting(SpecNode::id)
			.contains("should-attempt-fix", "retest", "build-health", "assess", "assess-join", "published",
					"early-published");
	}

	@Test
	void everyOperationDispatchIsConstructibleAgainstTheSelection() {
		var byCoordinate = new TreeMap<String, ProviderContract>();
		this.selection.contracts()
			.forEach(entry -> byCoordinate.put(key(entry.contract().coordinate()), entry.contract()));
		for (SpecNode node : this.spec.nodes()) {
			if (!(node instanceof OperationSpecNode operation)) {
				continue;
			}
			ProviderContractRef coordinate = this.spec.providers().get(operation.operation()).contract();
			ProviderContract contract = byCoordinate.get(key(coordinate));
			assertThat(contract).as("node %s resolves in the approved selection", node.id()).isNotNull();
			JsonNode schema = contract.input().schema();
			Set<String> keys = operation.input() == null ? Set.of() : operation.input().keySet();
			Set<String> required = new HashSet<>();
			schema.path("required").forEach(name -> required.add(name.asText()));
			Set<String> properties = new HashSet<>();
			schema.path("properties").fieldNames().forEachRemaining(properties::add);
			assertThat(keys).as("node %s covers required members", node.id()).containsAll(required);
			assertThat(properties).as("node %s stays within declared members", node.id()).containsAll(keys);
		}
	}

	@Test
	void bindingsChainTheRealStepOutputs() {
		OperationSpecNode rebase = (OperationSpecNode) this.spec.nodes().get(1);
		assertThat(rebase.id()).isEqualTo("rebase-on-main");
		assertThat(rebase.input()).containsEntry("prContext", new Binding("$node.fetch-pr-context.output"));
	}

	@Test
	void theServedViewJoinsIndependentSourceEvidence() {
		String recordHash = ContentDigest.sha256(this.artifactRecord);
		WorkflowView view = new WorkflowViewProjector().project(this.spec, this.selection, recordHash,
				this.artifactRecord);

		assertThat(view.evidence().workflowArtifactRecordHash()).isEqualTo(recordHash);
		assertThat(view.derived().labels()).isNotEmpty();
		assertThat(view.derived().ports()).isNotEmpty();
		assertThat(view.derived().sources()).hasSize(this.spec.nodes().size());
		assertThat(view.spec().nodes()).allSatisfy(
				node -> assertThat(view.derived().sources().get(node.id()).uri()).contains("PrReviewWorkflowV3.java"));
	}

	@Test
	void selectionConstructionRefusesAMissingProvider() {
		ProviderContractRef runTests = this.spec.providers().get("run-tests").contract();
		var withoutRunTests = this.catalog.contracts()
			.stream()
			.filter(entry -> !entry.contract().coordinate().equals(runTests))
			.toList();
		CuratedCatalog stripped = new CuratedCatalog(this.catalog.apiVersion(), this.catalog.kind(),
				this.catalog.workflows(), withoutRunTests, this.catalog.schemas());

		assertThatThrownBy(() -> new WorkflowCatalogSelector().select(stripped, "pr-review"))
			.isInstanceOf(CatalogSelectionException.class)
			.extracting(error -> ((CatalogSelectionException) error).code())
			.isEqualTo(CatalogSelectionException.Code.ZERO_MATCH);
	}

	private static String key(ProviderContractRef coordinate) {
		return coordinate.name() + "@" + coordinate.version();
	}

}
