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

/** Producer-side schema and coherence tests for every served v3alpha artifact. */
class WorkflowV3AlphaControllerTest {

	private static final String ID_PREFIX = "https://raw.githubusercontent.com/markpollack/agent-workflow/main/spec/v3alpha/";

	private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
			builder -> builder.schemaMappers(mappers -> mappers.mapPrefix(ID_PREFIX, "classpath:spec/v3alpha/")));

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowV3AlphaController()).build();

	@Test
	void servedViewValidatesAndCoheres() throws Exception {
		JsonNode view = getJson("/workflow/v3alpha/view");
		assertValid(view, "workflow-view.schema.json");
		assertThat(view.path("derived").path("specHash").asText())
			.isEqualTo(V3ServingFixtures.jcsDigest(view.get("spec")));
		assertThat(view.path("evidence").path("workflowArtifactRecordHash").asText()).startsWith("sha256:");
		assertThat(view.path("derived").path("sources")).hasSize(19);
		assertThat(view.path("diagnostics")).isEmpty();
	}

	@Test
	void servedCatalogValidatesAndCoheres() throws Exception {
		JsonNode catalog = getJson("/workflow/v3alpha/catalog");
		assertValid(catalog, "curated-catalog.schema.json");
		assertThat(catalog.path("workflows")).hasSize(1);
		assertThat(catalog.path("contracts")).hasSize(15);
		String previous = "";
		for (JsonNode entry : catalog.path("contracts")) {
			String name = entry.path("contract").path("name").asText();
			assertThat(name).isGreaterThan(previous);
			previous = name;
			for (String side : new String[] { "input", "output" }) {
				JsonNode carried = entry.path("contract").path(side);
				assertThat(carried.path("digest").asText())
					.isEqualTo(V3ServingFixtures.jcsDigest(carried.path("schema")));
			}
		}
	}

	@Test
	void servedSelectionAndArtifactRecordValidate() throws Exception {
		JsonNode selection = getJson("/workflow/v3alpha/selection");
		JsonNode record = getJson("/workflow/v3alpha/artifact-record");
		assertValid(selection, "workflow-catalog-selection.schema.json");
		assertValid(record, "workflow-artifact-record.schema.json");
		assertThat(selection.path("root").path("specHash")).isEqualTo(record.path("specHash"));
		assertThat(selection.path("contracts")).hasSize(15);
		assertThat(record.path("sources")).hasSize(19);
	}

	private void assertValid(JsonNode document, String schemaName) {
		JsonSchema schema = this.schemaFactory.getSchema(SchemaLocation.of(ID_PREFIX + schemaName));
		Set<ValidationMessage> messages = schema.validate(document);
		assertThat(messages).as("served document validates against %s", schemaName).isEmpty();
	}

	private JsonNode getJson(String uri) throws Exception {
		MvcResult result = this.mvc
			.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(uri))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
			.andReturn();
		return WireJson.mapper().readTree(result.getResponse().getContentAsString());
	}

}
