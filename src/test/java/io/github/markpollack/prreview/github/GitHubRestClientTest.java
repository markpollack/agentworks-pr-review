package io.github.markpollack.prreview.github;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.github.markpollack.prreview.config.GitHubProperties;
import io.github.markpollack.prreview.model.Comment;
import io.github.markpollack.prreview.model.FileChange;
import io.github.markpollack.prreview.model.Issue;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.Review;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class GitHubRestClientTest {

	private GitHubRestClient client;

	@BeforeEach
	void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
		GitHubProperties props = new GitHubProperties("spring-projects/spring-ai", wmRuntimeInfo.getHttpBaseUrl(),
				null);
		this.client = new GitHubRestClient(props, RestClient.builder());
	}

	@Nested
	class FetchPrContextTests {

		@Test
		void shouldAssembleCompletePrContext() {
			stubPr5774Endpoints();

			PrContext ctx = client.fetchPrContext(5774);

			assertThat(ctx.number()).isEqualTo(5774);
			assertThat(ctx.title()).isEqualTo("Propagate body-level errors in WebClientStreamableHttpTransport");
			assertThat(ctx.author()).isEqualTo("Planview-JamesK");
			assertThat(ctx.baseBranch()).isEqualTo("main");
			assertThat(ctx.headBranch()).isEqualTo("fix/889-body-error-propagation");
			assertThat(ctx.state()).isEqualTo("open");
			assertThat(ctx.files()).hasSize(2);
			assertThat(ctx.comments()).isEmpty();
			assertThat(ctx.reviews()).isEmpty();
		}

		@Test
		void shouldParseLinkedIssuesFromBody() {
			stubPr5774Endpoints();

			PrContext ctx = client.fetchPrContext(5774);

			// PR body contains "Fixes https://github.com/.../issues/889" — but our
			// regex matches "Fixes #NNN" form. The URL form isn't matched (by design).
			assertThat(ctx.linkedIssues()).isEmpty();
		}

	}

	@Nested
	class FetchFilesTests {

		@Test
		void shouldParseFileChanges() {
			stubFor(get(urlPathEqualTo("/repos/spring-projects/spring-ai/pulls/5774/files"))
				.willReturn(jsonResponse("fixtures/pr-5774-files.json")));

			List<FileChange> files = client.fetchFiles(5774);

			assertThat(files).hasSize(2);
			assertThat(files.get(0).filename()).contains("WebClientStreamableHttpTransport.java");
			assertThat(files.get(0).status()).isEqualTo("modified");
			assertThat(files.get(0).additions()).isEqualTo(17);
			assertThat(files.get(0).deletions()).isEqualTo(11);
			assertThat(files.get(0).patch()).isNotNull();
			assertThat(files.get(1).status()).isEqualTo("added");
		}

	}

	@Nested
	class FetchCommentsTests {

		@Test
		void shouldParseEmptyComments() {
			stubFor(get(urlPathEqualTo("/repos/spring-projects/spring-ai/issues/5774/comments"))
				.willReturn(jsonResponse("fixtures/pr-5774-comments.json")));

			List<Comment> comments = client.fetchComments(5774);

			assertThat(comments).isEmpty();
		}

		@Test
		void shouldParsePopulatedComments() {
			stubFor(get(urlPathEqualTo("/repos/spring-projects/spring-ai/issues/1175/comments"))
				.willReturn(jsonResponse("fixtures/pr-with-reviews-comments.json")));

			List<Comment> comments = client.fetchComments(1175);

			assertThat(comments).hasSize(2);
			assertThat(comments.get(0).author()).isEqualTo("maintainer-bob");
			assertThat(comments.get(0).body()).contains("add a test");
			assertThat(comments.get(0).createdAt()).isEqualTo(Instant.parse("2024-06-16T09:05:00Z"));
		}

	}

	@Nested
	class FetchReviewsTests {

		@Test
		void shouldParseEmptyReviews() {
			stubFor(get(urlPathEqualTo("/repos/spring-projects/spring-ai/pulls/5774/reviews"))
				.willReturn(jsonResponse("fixtures/pr-5774-reviews.json")));

			List<Review> reviews = client.fetchReviews(5774);

			assertThat(reviews).isEmpty();
		}

		@Test
		void shouldParsePopulatedReviews() {
			stubFor(get(urlPathEqualTo("/repos/spring-projects/spring-ai/pulls/1175/reviews"))
				.willReturn(jsonResponse("fixtures/pr-with-reviews-reviews.json")));

			List<Review> reviews = client.fetchReviews(1175);

			assertThat(reviews).hasSize(2);
			assertThat(reviews.get(0).author()).isEqualTo("maintainer-bob");
			assertThat(reviews.get(0).state()).isEqualTo("CHANGES_REQUESTED");
			assertThat(reviews.get(1).state()).isEqualTo("APPROVED");
		}

	}

	@Nested
	class RateLimitTests {

		@Test
		void shouldFetchRateLimitInfo() {
			stubFor(get(urlPathEqualTo("/rate_limit"))
				.willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
						{
						  "resources": {
						    "core": {
						      "limit": 5000,
						      "remaining": 4990,
						      "reset": 1712620800,
						      "used": 10
						    }
						  }
						}
						""")));

			GitHubRestClient.RateLimitInfo info = client.getRateLimit();

			assertThat(info.limit()).isEqualTo(5000);
			assertThat(info.remaining()).isEqualTo(4990);
			assertThat(info.hasHeadroom(15)).isTrue();
		}

	}

	@Nested
	class ParseLinkedIssuesTests {

		@Test
		void shouldParseFixesHashPattern() {
			List<Issue> issues = GitHubRestClient.parseLinkedIssues("Fixes #123 and also closes #456");
			assertThat(issues).hasSize(2);
			assertThat(issues.get(0).number()).isEqualTo(123);
			assertThat(issues.get(1).number()).isEqualTo(456);
		}

		@Test
		void shouldHandleNullBody() {
			assertThat(GitHubRestClient.parseLinkedIssues(null)).isEmpty();
		}

		@Test
		void shouldHandleNoMatches() {
			assertThat(GitHubRestClient.parseLinkedIssues("Just a description")).isEmpty();
		}

		@Test
		void shouldBeCaseInsensitive() {
			List<Issue> issues = GitHubRestClient.parseLinkedIssues("FIXES #100\nResolves #200");
			assertThat(issues).hasSize(2);
		}

	}

	// --- helpers ---

	private void stubPr5774Endpoints() {
		stubFor(get(urlPathEqualTo("/repos/spring-projects/spring-ai/pulls/5774"))
			.willReturn(jsonResponse("fixtures/pr-5774.json")));
		stubFor(get(urlPathEqualTo("/repos/spring-projects/spring-ai/pulls/5774/files"))
			.willReturn(jsonResponse("fixtures/pr-5774-files.json")));
		stubFor(get(urlPathEqualTo("/repos/spring-projects/spring-ai/issues/5774/comments"))
			.willReturn(jsonResponse("fixtures/pr-5774-comments.json")));
		stubFor(get(urlPathEqualTo("/repos/spring-projects/spring-ai/pulls/5774/reviews"))
			.willReturn(jsonResponse("fixtures/pr-5774-reviews.json")));
	}

	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder jsonResponse(
			String classpathResource) {
		try {
			String body = new ClassPathResource(classpathResource).getContentAsString(StandardCharsets.UTF_8);
			return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
		}
		catch (IOException ex) {
			throw new RuntimeException("Failed to load fixture: " + classpathResource, ex);
		}
	}

}
