package io.github.markpollack.prreview.v3;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.markpollack.agents.client.AgentClient;
import io.github.markpollack.prreview.config.GitHubProperties;
import io.github.markpollack.prreview.config.WorkshopProperties;
import io.github.markpollack.prreview.github.GitHubRestClient;
import io.github.markpollack.prreview.judges.BuildJudge;
import io.github.markpollack.prreview.judges.QualityJudge;
import io.github.markpollack.prreview.judges.VersionPatternJudge;
import io.github.markpollack.prreview.steps.AssessBackportStep;
import io.github.markpollack.prreview.steps.AssessCodeQualityStep;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.FixTestsStep;
import io.github.markpollack.prreview.steps.GenerateReportStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.prreview.steps.ShouldAttemptFixStep;
import io.github.markpollack.workflow.spec.CanonicalJson;
import io.github.markpollack.workflow.spec.v3.DefaultWorkflowSpecWriter;
import io.github.markpollack.workflow.spec.v3.WireJson;
import io.github.markpollack.workflow.spec.v3.WorkflowSpec;
import io.github.markpollack.workflow.spec.v3.envelope.OperationCatalog;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The served {@code workflow/v3alpha} document is what the v3 DSL emits, and it is
 * emitted by Spring from this application's own beans.
 *
 * <p>
 * Two claims, and they are different. That the <b>wiring</b> holds is
 * {@link #springAssemblesTheSpecFromTheApplicationsOwnBeans()}: {@code Workflow.build()}
 * refuses a graph whose bindings it cannot derive, so a leaf whose declared types stop
 * fitting the graph fails the context. That the <b>artifact</b> is current is
 * {@link #theCommittedSpecIsWhatTheDslEmitsRightNow()}: the resource the controller
 * serves is byte-compared against a fresh emission, because a generated file nobody
 * compares is a hand-authored file with extra steps. Regenerate deliberately:
 * {@code ./mvnw test -Dtest=PrReviewSpecV3Test -Dv3.spec.regenerate=true}, and read the
 * diff as a contract diff — {@code specHash} moves with it.
 */
class PrReviewSpecV3Test {

	private static final Path SERVED_SPEC = Path.of("src", "main", "resources", "v3alpha", "workflow-pr-review.json");

	private static final Path SERVED_CATALOG = Path.of("src", "main", "resources", "v3alpha", "operation-catalog.json");

	private static final String REGENERATE_PROPERTY = "v3.spec.regenerate";

	@Test
	void springAssemblesTheSpecFromTheApplicationsOwnBeans() {
		contextRunner().run(context -> assertThat(context).hasSingleBean(WorkflowSpec.class));
	}

	@Test
	void theCommittedSpecIsWhatTheDslEmitsRightNow() throws Exception {
		if (Boolean.getBoolean(REGENERATE_PROPERTY)) {
			regenerate();
		}
		assertMatchesCommitted(canonical(emit(context -> context.getBean(WorkflowSpec.class))), SERVED_SPEC);
	}

	/**
	 * The catalog is derived from the leaves this deployment actually holds, so it is
	 * checked the same way and for the same reason: a catalog nobody compares drifts into
	 * describing operations by the shapes they used to have.
	 */
	@Test
	void theCommittedCatalogIsWhatThisDeploymentDeploys() throws Exception {
		if (Boolean.getBoolean(REGENERATE_PROPERTY)) {
			regenerate();
		}
		assertMatchesCommitted(canonical(emit(context -> context.getBean(OperationCatalog.class))), SERVED_CATALOG);
	}

	private static void assertMatchesCommitted(byte[] emitted, Path committed) throws Exception {
		assertThat(new String(emitted, StandardCharsets.UTF_8))
			.as("the code is the source of truth for %s — if this diff is intended, regenerate with -D%s "
					+ "and review it as a contract diff", committed, REGENERATE_PROPERTY)
			.isEqualTo(new String(CanonicalJson.canonicalize(Files.readAllBytes(committed)), StandardCharsets.UTF_8));
	}

	/**
	 * Every binding in the served document was written by the emitter and by no author.
	 * The count is asserted rather than described because "we authored none" is a claim
	 * that decays silently: a hand-written {@code .reading(...)} added later would still
	 * emit a valid spec.
	 */
	@Test
	void everyBindingInTheServedSpecWasDerived() throws Exception {
		List<String> sources = new ArrayList<>();
		collectBindings(WireJson.mapper().readTree(Files.readAllBytes(SERVED_SPEC)), sources);

		assertThat(sources).isNotEmpty();
		assertThat(sources).filteredOn(s -> s.startsWith("$input")).containsExactly("$input");
		// The one context key in the pipeline, read where the value is "whichever of
		// run-tests / retest ran" and no node output can stand for it.
		assertThat(sources).filteredOn(s -> s.startsWith("$context"))
			.isNotEmpty()
			.allMatch("$context.build.result"::equals);
	}

	/**
	 * §8.2 provenance survived the trip into a second repository: every node in the
	 * served document names the line of <em>this</em> repository's DSL that declared it.
	 */
	@Test
	void everyNodeInTheServedSpecCarriesItsDeclarationSiteInThisRepository() throws Exception {
		JsonNode document = WireJson.mapper().readTree(Files.readAllBytes(SERVED_SPEC));
		JsonNode nodes = document.get("nodes");

		assertThat(nodes).isNotEmpty();
		nodes.forEach(node -> {
			JsonNode source = node.get("source");
			assertThat(source).as("node '%s' carries a source", node.get("id").asText()).isNotNull();
			assertThat(Path.of(source.get("uri").asText()))
				.as("node '%s' source.uri resolves in this repository", node.get("id").asText())
				.isRegularFile();
			assertThat(source.get("startLine").asInt()).isPositive();
		});
	}

	// ── emission ─────────────────────────────────────────────────────────

	private static <T> T emit(Function<ApplicationContext, T> read) {
		Object[] emitted = new Object[1];
		contextRunner().run(context -> emitted[0] = read.apply(context));
		@SuppressWarnings("unchecked")
		T value = (T) emitted[0];
		return value;
	}

	/**
	 * The application's own configuration, with the two collaborators that reach the
	 * network stubbed. Everything the workflow is composed of — every leaf, the jury, the
	 * fix policy — is the bean the running application uses, because what this test is
	 * checking is that the surface survives a container.
	 */
	private static ApplicationContextRunner contextRunner() {
		// The committed defaults from application.yml: the emitted artifact is the
		// artifact for *this* configuration. workshop.fix-tests reaches the spec as node
		// config, so a deployment that flips it is a different workflow and must re-emit.
		WorkshopProperties workshop = new WorkshopProperties(5774, false, "./journal", ".", true);
		AgentClient agentClient = mock(AgentClient.class);
		GitHubRestClient gitHubClient = new GitHubRestClient(
				new GitHubProperties("owner/repo", "https://api.github.com", null));

		return new ApplicationContextRunner().withUserConfiguration(PrReviewV3Config.class)
			.withBean(WorkshopProperties.class, () -> workshop)
			.withBean(AgentClient.class, () -> agentClient)
			.withBean(GitHubRestClient.class, () -> gitHubClient)
			.withBean(BuildJudge.class, BuildJudge::new)
			.withBean(QualityJudge.class, () -> new QualityJudge(agentClient))
			.withBean(VersionPatternJudge.class, VersionPatternJudge::new)
			.withBean(FetchPrContextStep.class, () -> new FetchPrContextStep(gitHubClient))
			.withBean(RebaseStep.class, () -> new RebaseStep(workshop))
			.withBean(ConflictDetectionStep.class, ConflictDetectionStep::new)
			.withBean(RunTestsStep.class, () -> new RunTestsStep(workshop))
			.withBean(ShouldAttemptFixStep.class, ShouldAttemptFixStep::new)
			.withBean(FixTestsStep.class, () -> new FixTestsStep(agentClient, workshop))
			.withBean(AssessCodeQualityStep.class, () -> new AssessCodeQualityStep(agentClient))
			.withBean(AssessBackportStep.class, () -> new AssessBackportStep(agentClient))
			.withBean(GenerateReportStep.class, GenerateReportStep::new);
	}

	/**
	 * Pretty-printed <em>from the canonical bytes</em>, never from the model: map
	 * iteration order is JVM-salt randomized, so printing the model would emit a
	 * different (equally valid) file every run, and an artifact that churns is worse than
	 * none.
	 */
	private static void regenerate() throws Exception {
		write(canonical(emit(context -> context.getBean(WorkflowSpec.class))), SERVED_SPEC);
		write(canonical(emit(context -> context.getBean(OperationCatalog.class))), SERVED_CATALOG);
	}

	private static void write(byte[] canonical, Path target) throws Exception {
		Files.createDirectories(target.getParent());
		String pretty = WireJson.mapper()
			.writerWithDefaultPrettyPrinter()
			.writeValueAsString(WireJson.mapper().readTree(canonical));
		Files.writeString(target, pretty + "\n");
		System.out.println("[v3] regenerated " + target.toAbsolutePath().normalize());
	}

	/**
	 * The spec goes out through the contract's own writer; the catalog is a producer
	 * envelope with no writer of its own, so it goes out through the contract-side
	 * mapper. Both end at RFC 8785 canonical bytes, which is what any of this is compared
	 * as.
	 */
	private static byte[] canonical(Object document) throws Exception {
		if (document instanceof WorkflowSpec spec) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			new DefaultWorkflowSpecWriter().write(spec, out);
			return CanonicalJson.canonicalize(out.toByteArray());
		}
		return CanonicalJson.canonicalize(WireJson.mapper().writeValueAsBytes(document));
	}

	/** Every {@code Binding}: an object carrying {@code from} and no edge {@code to}. */
	private static void collectBindings(JsonNode node, List<String> sources) {
		if (node.isObject() && node.has("from") && !node.has("to")) {
			sources.add(node.get("from").asText());
		}
		node.forEach(child -> collectBindings(child, sources));
	}

}
