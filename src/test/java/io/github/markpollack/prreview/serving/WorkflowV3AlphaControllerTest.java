package io.github.markpollack.prreview.serving;

import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.github.markpollack.workflow.spec.v3.WireJson;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Producer-side contract tests (CONTRACT C-2/§19; ROADMAP 1.2): every served payload
 * validates against the committed exemplar schemas (loaded from the workflow-spec jar —
 * the same artifacts the engine's own gate runs), and the served view/catalog cohere:
 * real specHash, real digests, ordered entries, zero advisories against this deployment's
 * own catalog instance.
 */
class WorkflowV3AlphaControllerTest {

	private static final String ID_PREFIX = "https://raw.githubusercontent.com/markpollack/agent-workflow/main/spec/v3alpha/";

	private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
			builder -> builder.schemaMappers(mappers -> mappers.mapPrefix(ID_PREFIX, "classpath:spec/v3alpha/")));

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowV3AlphaController()).build();

	@Test
	void servedViewValidatesAgainstTheCommittedProducerSchema() throws Exception {
		JsonNode view = getJson("/workflow/v3alpha/view");

		JsonSchema schema = this.schemaFactory.getSchema(SchemaLocation.of(ID_PREFIX + "workflow-view.schema.json"));
		Set<ValidationMessage> messages = schema.validate(view);
		assertThat(messages).as("served WorkflowView validates against workflow-view.schema.json").isEmpty();
	}

	@Test
	void servedViewCoheres() throws Exception {
		JsonNode view = getJson("/workflow/v3alpha/view");

		assertThat(view.get("derived").get("specHash").asText())
			.as("derived.specHash is the real C-6 digest of the embedded spec")
			.isEqualTo(V3ServingFixtures.jcsDigest(view.get("spec")));
		assertThat(view.get("diagnostics")).as("the deployment's own catalog resolves every ref — zero advisories")
			.isEmpty();
		assertThat(view.get("spec").get("metadata").get("name").asText()).isEqualTo("pr-review");
	}

	@Test
	void servedCatalogValidatesAndCoheres() throws Exception {
		JsonNode catalog = getJson("/workflow/v3alpha/catalog");

		JsonSchema schema = this.schemaFactory
			.getSchema(SchemaLocation.of(ID_PREFIX + "operation-catalog.schema.json"));
		assertThat(schema.validate(catalog)).as("served catalog validates against operation-catalog.schema.json")
			.isEmpty();

		assertThat(catalog.get("instance").get("id").asText()).isEqualTo("agentworks-pr-review-app@2026-07-23.1");
		String previousRef = "";
		for (JsonNode entry : catalog.get("operations")) {
			assertThat(entry.get("ref").asText()).as("entries ordered lexicographically by ref (§13.1)")
				.isGreaterThan(previousRef);
			previousRef = entry.get("ref").asText();
			for (String side : new String[] { "input", "output" }) {
				assertThat(entry.get(side).get("digest").asText())
					.as("%s %s digest is the real sha256 over JCS bytes (C-6)", entry.get("ref").asText(), side)
					.isEqualTo(V3ServingFixtures.jcsDigest(entry.get(side).get("schema")));
			}
		}
	}

	private JsonNode getJson(String uri) throws Exception {
		MvcResult result = this.mvc
			.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(uri))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
			.andReturn();
		return WireJson.mapper().readTree(result.getResponse().getContentAsString());
	}

}
