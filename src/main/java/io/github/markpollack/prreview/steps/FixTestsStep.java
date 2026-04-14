package io.github.markpollack.prreview.steps;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import io.github.markpollack.prreview.config.WorkshopProperties;
import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.FixResult;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agents.client.AgentClient;
import org.springaicommunity.agents.client.AgentClientResponse;

import org.springframework.stereotype.Component;

/**
 * AI-powered step that attempts to fix failing tests.
 *
 * <p>
 * When tests fail during the PR review pipeline, this step invokes Claude to diagnose the
 * failure and fix the test code. It only modifies test files — never production code. The
 * agent runs in the checked-out PR branch directory so it can read and edit files
 * directly.
 */
@Component
public class FixTestsStep implements Step<BuildResult, FixResult> {

	private static final Logger logger = LoggerFactory.getLogger(FixTestsStep.class);

	private static final String PROMPT_TEMPLATE = loadTemplate("prompts/fix-test-failures.md");

	private final AgentClient agentClient;

	private final Path workingDirectory;

	private AgentClientResponse lastResponse;

	public FixTestsStep(AgentClient agentClient, WorkshopProperties workshopProperties) {
		this.agentClient = agentClient;
		this.workingDirectory = Path.of(workshopProperties.repoDir());
	}

	public AgentClientResponse lastResponse() {
		return this.lastResponse;
	}

	@Override
	public String name() {
		return "fix-tests";
	}

	@Override
	public FixResult execute(AgentContext ctx, BuildResult input) {
		if (input.success() || input.skipped()) {
			return FixResult.notNeeded();
		}

		PrContext prContext = ctx.require(FetchPrContextStep.PR_CONTEXT);
		logger.info("Attempting AI fix for test failures in PR #{}", prContext.number());

		String prompt = renderPrompt(prContext, input);
		try {
			AgentClientResponse response = this.agentClient.goal(prompt).workingDirectory(this.workingDirectory).run();
			this.lastResponse = response;
			String result = response.getResult();
			logger.info("AI fix-tests complete for PR #{}", prContext.number());
			return parseResult(result);
		}
		catch (Exception ex) {
			logger.error("AI fix-tests failed for PR #{}: {}", prContext.number(), ex.getMessage());
			return new FixResult(false, false, List.of(), "Fix failed: " + ex.getMessage());
		}
	}

	static String renderPrompt(PrContext pr, BuildResult build) {
		String output = build.output() != null ? build.output() : "(no output captured)";
		return PROMPT_TEMPLATE.replace("{number}", String.valueOf(pr.number()))
			.replace("{title}", pr.title())
			.replace("{author}", pr.author())
			.replace("{modules}", String.join(", ", build.modules()))
			.replace("{testOutput}", output)
			.replace("{outputLength}", String.valueOf(output.length()));
	}

	private static FixResult parseResult(String json) {
		try {
			// Simple JSON parsing — look for "fixed": true/false and "summary"
			boolean fixed = json.contains("\"fixed\": true") || json.contains("\"fixed\":true");
			String summary = extractJsonString(json, "summary");
			List<String> filesChanged = List.of(); // simplified
			return new FixResult(true, fixed, filesChanged, summary);
		}
		catch (Exception ex) {
			return new FixResult(true, false, List.of(), "Could not parse AI response: " + ex.getMessage());
		}
	}

	private static String extractJsonString(String json, String key) {
		String search = "\"" + key + "\"";
		int idx = json.indexOf(search);
		if (idx < 0) {
			return "";
		}
		int colonIdx = json.indexOf(':', idx + search.length());
		if (colonIdx < 0) {
			return "";
		}
		int startQuote = json.indexOf('"', colonIdx + 1);
		if (startQuote < 0) {
			return "";
		}
		int endQuote = json.indexOf('"', startQuote + 1);
		if (endQuote < 0) {
			return "";
		}
		return json.substring(startQuote + 1, endQuote);
	}

	private static String loadTemplate(String resource) {
		try (InputStream is = FixTestsStep.class.getClassLoader().getResourceAsStream(resource)) {
			if (is == null) {
				return "Fix the failing tests in this PR.";
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			return "Fix the failing tests in this PR.";
		}
	}

	@Override
	public Class<?> inputType() {
		return BuildResult.class;
	}

	@Override
	public Class<?> outputType() {
		return FixResult.class;
	}

}
