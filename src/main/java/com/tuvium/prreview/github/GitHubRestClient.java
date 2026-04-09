package com.tuvium.prreview.github;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import com.tuvium.prreview.config.GitHubProperties;
import com.tuvium.prreview.model.Comment;
import com.tuvium.prreview.model.FileChange;
import com.tuvium.prreview.model.Issue;
import com.tuvium.prreview.model.PrContext;
import com.tuvium.prreview.model.Review;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * GitHub REST API client for fetching PR metadata, files, comments, and reviews.
 *
 * <p>
 * Uses Spring's {@link RestClient} with JSON response parsing via Jackson
 * {@link JsonNode}. Supports optional {@code GITHUB_TOKEN} for higher rate limits
 * (critical for workshops with 20+ participants).
 */
@Component
public class GitHubRestClient {

	private static final Logger logger = LoggerFactory.getLogger(GitHubRestClient.class);

	private final RestClient restClient;

	private final GitHubProperties properties;

	public GitHubRestClient(GitHubProperties properties, RestClient.Builder builder) {
		RestClient.Builder clientBuilder = builder.baseUrl(properties.baseUrl())
			.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3+json");

		if (properties.token() != null && !properties.token().isBlank()) {
			clientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token());
			logger.info("GitHub client configured with authentication token");
		}
		else {
			logger.warn("GitHub client running without token — rate limit is 60 req/hr");
		}

		this.restClient = clientBuilder.build();
		this.properties = properties;
	}

	/**
	 * Fetches a complete PR context by assembling metadata, files, comments, reviews, and
	 * linked issues.
	 */
	public PrContext fetchPrContext(int prNumber) {
		logger.info("Fetching PR #{} from {}", prNumber, this.properties.repo());
		JsonNode pr = get("/repos/{owner}/{repo}/pulls/{number}", prNumber);
		List<FileChange> files = fetchFiles(prNumber);
		List<Comment> comments = fetchComments(prNumber);
		List<Review> reviews = fetchReviews(prNumber);
		List<Issue> linkedIssues = parseLinkedIssues(pr.path("body").asText(""));

		return new PrContext(pr.path("number").asInt(), pr.path("title").asText(), nullableText(pr.path("body")),
				pr.path("user").path("login").asText("unknown"), parseLabels(pr.path("labels")),
				pr.path("state").asText(), pr.path("base").path("ref").asText(), pr.path("head").path("ref").asText(),
				files, comments, reviews, linkedIssues);
	}

	/**
	 * Fetches changed files for a PR with pagination.
	 */
	public List<FileChange> fetchFiles(int prNumber) {
		List<FileChange> all = new ArrayList<>();
		int page = 1;
		while (true) {
			JsonNode nodes = get("/repos/{owner}/{repo}/pulls/{number}/files?per_page=100&page={page}", prNumber, page);
			if (!nodes.isArray() || nodes.isEmpty()) {
				break;
			}
			for (JsonNode node : nodes) {
				all.add(new FileChange(node.path("filename").asText(), node.path("status").asText(),
						node.path("additions").asInt(), node.path("deletions").asInt(),
						nullableText(node.path("patch"))));
			}
			if (nodes.size() < 100) {
				break;
			}
			page++;
		}
		logger.debug("Fetched {} files for PR #{}", all.size(), prNumber);
		return all;
	}

	/**
	 * Fetches issue-level comments (not inline review comments). Line-level review
	 * comments live at {@code /pulls/{n}/comments} and are intentionally omitted — review
	 * summaries from {@link #fetchReviews} capture the review state and body.
	 */
	public List<Comment> fetchComments(int prNumber) {
		JsonNode nodes = get("/repos/{owner}/{repo}/issues/{number}/comments?per_page=100", prNumber);
		List<Comment> comments = new ArrayList<>();
		if (nodes.isArray()) {
			for (JsonNode node : nodes) {
				comments.add(new Comment(node.path("user").path("login").asText("unknown"),
						node.path("body").asText(""), parseInstant(node.path("created_at").asText(null))));
			}
		}
		logger.debug("Fetched {} comments for PR #{}", comments.size(), prNumber);
		return comments;
	}

	/**
	 * Fetches reviews for a PR.
	 */
	public List<Review> fetchReviews(int prNumber) {
		JsonNode nodes = get("/repos/{owner}/{repo}/pulls/{number}/reviews?per_page=100", prNumber);
		List<Review> reviews = new ArrayList<>();
		if (nodes.isArray()) {
			for (JsonNode node : nodes) {
				reviews.add(new Review(node.path("user").path("login").asText("unknown"), node.path("state").asText(""),
						node.path("body").asText(""), parseInstant(node.path("submitted_at").asText(null))));
			}
		}
		logger.debug("Fetched {} reviews for PR #{}", reviews.size(), prNumber);
		return reviews;
	}

	/**
	 * Returns current rate limit information for the pre-flight check.
	 */
	public RateLimitInfo getRateLimit() {
		JsonNode node = this.restClient.get().uri("/rate_limit").retrieve().body(JsonNode.class);
		if (node == null) {
			return new RateLimitInfo(0, 0, Instant.EPOCH);
		}
		JsonNode core = node.path("resources").path("core");
		return new RateLimitInfo(core.path("limit").asInt(), core.path("remaining").asInt(),
				Instant.ofEpochSecond(core.path("reset").asLong()));
	}

	private JsonNode get(String uriTemplate, Object... uriVariables) {
		Object[] allVars = expandUriVariables(uriVariables);
		JsonNode result = this.restClient.get().uri(uriTemplate, allVars).retrieve().body(JsonNode.class);
		return (result != null) ? result : tools.jackson.databind.node.JsonNodeFactory.instance.missingNode();
	}

	/**
	 * Prepends owner and repo to URI variables. Templates use {owner}, {repo}, then
	 * caller-supplied variables.
	 */
	private Object[] expandUriVariables(Object... callerVars) {
		Object[] expanded = new Object[callerVars.length + 2];
		expanded[0] = this.properties.owner();
		expanded[1] = this.properties.repoName();
		System.arraycopy(callerVars, 0, expanded, 2, callerVars.length);
		return expanded;
	}

	private static List<String> parseLabels(JsonNode labelsNode) {
		List<String> labels = new ArrayList<>();
		if (labelsNode.isArray()) {
			for (JsonNode label : labelsNode) {
				labels.add(label.path("name").asText());
			}
		}
		return labels;
	}

	/**
	 * Extracts issue numbers from "Fixes #NNN" / "Closes #NNN" patterns in PR body.
	 * Returns empty issues with just the number — full issue fetch is deferred.
	 */
	static List<Issue> parseLinkedIssues(String body) {
		List<Issue> issues = new ArrayList<>();
		if (body == null || body.isBlank()) {
			return issues;
		}
		java.util.regex.Matcher matcher = java.util.regex.Pattern
			.compile("(?i)(?:fix(?:es)?|close[sd]?|resolve[sd]?)\\s+#(\\d+)")
			.matcher(body);
		while (matcher.find()) {
			int number = Integer.parseInt(matcher.group(1));
			issues.add(new Issue(number, "", List.of()));
		}
		return issues;
	}

	private static @Nullable String nullableText(JsonNode node) {
		return (node.isMissingNode() || node.isNull()) ? null : node.asText();
	}

	private static Instant parseInstant(@Nullable String text) {
		if (text == null || text.isEmpty()) {
			return Instant.EPOCH;
		}
		return Instant.parse(text);
	}

	/**
	 * Rate limit information from the GitHub API.
	 *
	 * @param limit max requests per hour
	 * @param remaining remaining requests in current window
	 * @param resetAt when the rate limit window resets
	 */
	public record RateLimitInfo(int limit, int remaining, Instant resetAt) {

		public boolean hasHeadroom(int needed) {
			return this.remaining >= needed;
		}

	}

}
