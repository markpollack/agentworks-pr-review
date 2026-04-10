package io.github.markpollack.prreview;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.markpollack.prreview.config.GitHubProperties;
import io.github.markpollack.prreview.config.WorkshopProperties;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Validates that all prerequisites are met before running the PR review pipeline.
 *
 * <p>
 * Checks: Java version, Git availability, GitHub API access, rate limit headroom, target
 * repo clone, and optionally Claude Code CLI presence.
 */
@Component
public class PreflightCheck {

	private static final Logger logger = LoggerFactory.getLogger(PreflightCheck.class);

	private static final int MIN_RATE_LIMIT_REMAINING = 30;

	private final GitHubProperties gitHubProperties;

	private final WorkshopProperties workshopProperties;

	public PreflightCheck(GitHubProperties gitHubProperties, WorkshopProperties workshopProperties) {
		this.gitHubProperties = gitHubProperties;
		this.workshopProperties = workshopProperties;
	}

	/**
	 * Runs all pre-flight checks and returns the results.
	 * @return list of check results (name, passed, message)
	 */
	public List<CheckResult> run() {
		List<CheckResult> results = new ArrayList<>();

		results.add(checkJavaVersion());
		results.add(checkGit());
		results.add(checkRepoDir());
		results.add(checkGitHubApi());
		results.add(checkRateLimit());
		results.add(checkClaudeCodeCli());

		return results;
	}

	/**
	 * Runs checks and logs results. Returns true if all critical checks pass.
	 */
	public boolean runAndReport() {
		List<CheckResult> results = run();

		logger.info("=== Pre-flight Check Results ===");
		boolean allCriticalPass = true;
		for (CheckResult result : results) {
			String status = result.passed() ? "PASS" : (result.critical() ? "FAIL" : "WARN");
			logger.info("  [{}] {} — {}", status, result.name(), result.message());
			if (!result.passed() && result.critical()) {
				allCriticalPass = false;
			}
		}
		logger.info("================================");

		if (allCriticalPass) {
			logger.info("All critical checks passed. Ready to run.");
		}
		else {
			logger.error("Some critical checks failed. Fix issues before running.");
		}

		return allCriticalPass;
	}

	private CheckResult checkJavaVersion() {
		String version = System.getProperty("java.version");
		int major = Runtime.version().feature();
		if (major >= 21) {
			return CheckResult.pass("Java Version", "Java " + version + " (>= 21)");
		}
		return CheckResult.criticalFail("Java Version", "Java " + version + " — requires Java 21+");
	}

	private CheckResult checkGit() {
		try {
			Process process = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
			String output = new String(process.getInputStream().readAllBytes()).trim();
			int exitCode = process.waitFor();
			if (exitCode == 0) {
				return CheckResult.pass("Git", output);
			}
			return CheckResult.criticalFail("Git", "git --version failed with exit code " + exitCode);
		}
		catch (Exception ex) {
			return CheckResult.criticalFail("Git", "git not found: " + ex.getMessage());
		}
	}

	private CheckResult checkRepoDir() {
		Path repoDir = Path.of(this.workshopProperties.repoDir());
		if (!Files.isDirectory(repoDir)) {
			return CheckResult.criticalFail("Repo Clone",
					"workshop.repo-dir=" + repoDir + " does not exist. Clone the target repo first.");
		}
		Path gitDir = repoDir.resolve(".git");
		if (!Files.isDirectory(gitDir)) {
			return CheckResult.criticalFail("Repo Clone", repoDir + " is not a git repository (no .git directory).");
		}
		try {
			Process process = new ProcessBuilder("git", "remote", "get-url", "origin").directory(repoDir.toFile())
				.redirectErrorStream(true)
				.start();
			String output = new String(process.getInputStream().readAllBytes()).trim();
			int exitCode = process.waitFor();
			if (exitCode == 0 && output.contains(this.gitHubProperties.repoName())) {
				return CheckResult.pass("Repo Clone", repoDir + " — origin: " + output);
			}
			return CheckResult.criticalFail("Repo Clone",
					repoDir + " origin (" + output + ") does not match " + this.gitHubProperties.repo());
		}
		catch (Exception ex) {
			return CheckResult.criticalFail("Repo Clone", "Could not verify git remote: " + ex.getMessage());
		}
	}

	private CheckResult checkGitHubApi() {
		try {
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(this.gitHubProperties.baseUrl() + "/repos/" + this.gitHubProperties.repo()))
				.GET();
			addTokenIfPresent(requestBuilder);

			HttpResponse<String> response = HttpClient.newHttpClient()
				.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() == 200) {
				return CheckResult.pass("GitHub API", "Successfully accessed " + this.gitHubProperties.repo());
			}
			return CheckResult.criticalFail("GitHub API",
					"HTTP " + response.statusCode() + " accessing " + this.gitHubProperties.repo());
		}
		catch (Exception ex) {
			return CheckResult.criticalFail("GitHub API", "Cannot reach GitHub API: " + ex.getMessage());
		}
	}

	private CheckResult checkRateLimit() {
		try {
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(this.gitHubProperties.baseUrl() + "/rate_limit"))
				.GET();
			addTokenIfPresent(requestBuilder);

			HttpResponse<String> response = HttpClient.newHttpClient()
				.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() == 200) {
				String body = response.body();
				int remaining = extractJsonInt(body, "remaining");
				int limit = extractJsonInt(body, "limit");

				boolean hasToken = this.gitHubProperties.token() != null;
				String tokenStatus = hasToken ? "authenticated" : "unauthenticated";

				if (remaining >= MIN_RATE_LIMIT_REMAINING) {
					return CheckResult.pass("Rate Limit", remaining + "/" + limit + " remaining (" + tokenStatus + ")");
				}
				return CheckResult.criticalFail("Rate Limit", "Only " + remaining + "/" + limit + " remaining ("
						+ tokenStatus + "). Set GITHUB_TOKEN for higher limits.");
			}
			return CheckResult.warnFail("Rate Limit",
					"Could not check rate limit (HTTP " + response.statusCode() + ")");
		}
		catch (Exception ex) {
			return CheckResult.warnFail("Rate Limit", "Could not check rate limit: " + ex.getMessage());
		}
	}

	private CheckResult checkClaudeCodeCli() {
		try {
			Process process = new ProcessBuilder("claude", "--version").redirectErrorStream(true).start();
			String output = new String(process.getInputStream().readAllBytes()).trim();
			int exitCode = process.waitFor();
			if (exitCode == 0) {
				return CheckResult.pass("Claude Code CLI", output);
			}
			return CheckResult.warnFail("Claude Code CLI",
					"claude --version failed (exit " + exitCode + "). AI assessment will not work.");
		}
		catch (Exception ex) {
			return CheckResult.warnFail("Claude Code CLI",
					"claude not found. AI assessment will not work — use --workshop.skip-ai=true for deterministic-only mode.");
		}
	}

	private void addTokenIfPresent(HttpRequest.Builder builder) {
		if (this.gitHubProperties.token() != null && !this.gitHubProperties.token().isBlank()) {
			builder.header("Authorization", "Bearer " + this.gitHubProperties.token());
		}
	}

	/**
	 * Quick-and-dirty JSON int extraction without Jackson dependency. Finds first
	 * occurrence of "key": number pattern in the rate_limit core section.
	 */
	static int extractJsonInt(String json, String key) {
		// Look for the "core" section first, then find the key
		int coreIdx = json.indexOf("\"core\"");
		if (coreIdx < 0) {
			coreIdx = 0;
		}
		String searchKey = "\"" + key + "\"";
		int keyIdx = json.indexOf(searchKey, coreIdx);
		if (keyIdx < 0) {
			return -1;
		}
		int colonIdx = json.indexOf(':', keyIdx + searchKey.length());
		if (colonIdx < 0) {
			return -1;
		}
		StringBuilder num = new StringBuilder();
		for (int i = colonIdx + 1; i < json.length(); i++) {
			char c = json.charAt(i);
			if (Character.isDigit(c)) {
				num.append(c);
			}
			else if (num.length() > 0) {
				break;
			}
		}
		return num.length() > 0 ? Integer.parseInt(num.toString()) : -1;
	}

	public record CheckResult(String name, boolean passed, boolean critical, String message) {

		static CheckResult pass(String name, String message) {
			return new CheckResult(name, true, true, message);
		}

		static CheckResult criticalFail(String name, String message) {
			return new CheckResult(name, false, true, message);
		}

		static CheckResult warnFail(String name, String message) {
			return new CheckResult(name, false, false, message);
		}

	}

}
