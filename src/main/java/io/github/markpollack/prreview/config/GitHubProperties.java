package io.github.markpollack.prreview.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GitHub API configuration properties.
 *
 * @param repo owner/name (e.g., "spring-projects/spring-ai")
 * @param baseUrl API base URL (default: https://api.github.com)
 * @param token optional personal access token (higher rate limits)
 */
@ConfigurationProperties(prefix = "github")
public record GitHubProperties(String repo, String baseUrl, @Nullable String token) {

	public String owner() {
		return repo.split("/")[0];
	}

	public String repoName() {
		return repo.split("/")[1];
	}

}
