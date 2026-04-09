package com.tuvium.prreview.model;

import java.time.Instant;
import java.util.List;

/**
 * Factory methods for sample {@link PrContext} instances used in tests.
 *
 * <p>
 * Modeled after real spring-ai PRs. JSON fixtures in {@code src/test/resources/fixtures/}
 * follow the GitHub REST API response format (snake_case, matching
 * {@code github-collector} conventions).
 */
public final class TestPrContexts {

	private TestPrContexts() {
	}

	/**
	 * PR 5774 — a small WebFlux transport fix. Two files changed, no reviews, no
	 * comments.
	 */
	public static PrContext pr5774() {
		return new PrContext(5774, "Propagate body-level errors in WebClientStreamableHttpTransport",
				"When WebClientStreamableHttpTransport.sendMessage() encounters a body-level error, "
						+ "the onErrorComplete operator silently swallows the error.",
				"Planview-JamesK", List.of(), "open", "main", "fix/889-body-error-propagation",
				List.of(new FileChange(
						"mcp/transport/mcp-spring-webflux/src/main/java/org/springframework/ai/mcp/client/webflux/transport/WebClientStreamableHttpTransport.java",
						"modified", 17, 11, "@@ -314,6 +314,9 @@"),
						new FileChange(
								"mcp/transport/mcp-spring-webflux/src/test/java/org/springframework/ai/mcp/client/webflux/transport/WebClientStreamableHttpTransportBodyErrorIT.java",
								"added", 230, 0, "@@ -0,0 +1,230 @@")),
				List.of(), List.of(), List.of());
	}

	/**
	 * A merged PR with reviews, comments, and labels — exercises all PrContext fields.
	 */
	public static PrContext prWithReviews() {
		return new PrContext(1175, "Add support for Ollama function calling",
				"This PR adds function calling support for the Ollama chat model.", "contributor-alice",
				List.of("type: enhancement", "module: ollama"), "closed", "main", "feature/ollama-function-calling",
				List.of(new FileChange("models/spring-ai-ollama/src/main/java/OllamaToolSupport.java", "added", 200, 0,
						null),
						new FileChange("models/spring-ai-ollama/src/test/java/OllamaToolSupportTest.java", "added", 150,
								0, null)),
				List.of(new Comment("maintainer-bob",
						"Could you add a test for the error case when the model doesn't support tools?",
						Instant.parse("2024-06-16T09:05:00Z")),
						new Comment("contributor-alice", "Good point, added in the latest commit.",
								Instant.parse("2024-06-17T15:30:00Z"))),
				List.of(new Review("maintainer-bob", "CHANGES_REQUESTED",
						"A few things to address before merging — see inline comments.",
						Instant.parse("2024-06-16T09:00:00Z")),
						new Review("maintainer-bob", "APPROVED", "Looks good now, thanks for the changes!",
								Instant.parse("2024-06-18T13:00:00Z"))),
				List.of(new Issue(1100, "Support function calling in Ollama", List.of("type: enhancement"))));
	}

	/** Minimal PR — bare-minimum fields for testing edge cases. */
	public static PrContext minimal() {
		return new PrContext(1, "Minimal PR", null, "author", List.of(), "open", "main", "fix", List.of(), List.of(),
				List.of(), List.of());
	}

}
