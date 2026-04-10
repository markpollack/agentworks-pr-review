package io.github.markpollack.prreview.steps;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.workflow.flows.AgentContext;
import io.github.markpollack.workflow.flows.ContextKey;
import io.github.markpollack.workflow.flows.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agents.client.AgentClient;
import org.springaicommunity.agents.client.AgentClientResponse;
import org.springaicommunity.judge.result.JudgmentStatus;

import org.springframework.stereotype.Component;

/**
 * AI-powered code quality assessment using AgentClient.
 *
 * <p>
 * Renders the quality prompt template with PR context, sends it to Claude via
 * AgentClient, and parses the structured JSON response into an {@link AssessmentResult}.
 * Only runs if T0 and T1 judges have passed.
 */
@Component
public class AssessCodeQualityStep implements Step<PrContext, AssessmentResult> {

	public static final ContextKey<AssessmentResult> QUALITY_ASSESSMENT = ContextKey.of("qualityAssessment",
			AssessmentResult.class);

	private static final Logger logger = LoggerFactory.getLogger(AssessCodeQualityStep.class);

	private static final String PROMPT_TEMPLATE = loadTemplate("prompts/code-quality-assessment.md");

	private final AgentClient agentClient;

	private AgentClientResponse lastResponse;

	public AssessCodeQualityStep(AgentClient agentClient) {
		this.agentClient = agentClient;
	}

	public AgentClientResponse lastResponse() {
		return this.lastResponse;
	}

	@Override
	public String name() {
		return "assess-code-quality";
	}

	@Override
	public AssessmentResult execute(AgentContext ctx, PrContext input) {
		logger.info("Running code quality assessment for PR #{}", input.number());

		String prompt = renderPrompt(input);
		try {
			AgentClientResponse response = this.agentClient.run(prompt);
			this.lastResponse = response;
			String result = response.getResult();
			logger.info("Quality assessment complete for PR #{}", input.number());
			return AssessmentParser.parse("code-quality", result);
		}
		catch (Exception ex) {
			logger.error("Quality assessment failed for PR #{}: {}", input.number(), ex.getMessage());
			return new AssessmentResult("code-quality", JudgmentStatus.ERROR, 0.0,
					"Assessment failed: " + ex.getMessage(), List.of());
		}
	}

	@Override
	public AgentContext updateContext(AgentContext ctx, AssessmentResult output) {
		return ctx.mutate().with(QUALITY_ASSESSMENT, output).build();
	}

	static String renderPrompt(PrContext pr) {
		return PROMPT_TEMPLATE.replace("{number}", String.valueOf(pr.number()))
			.replace("{title}", pr.title())
			.replace("{author}", pr.author())
			.replace("{baseBranch}", pr.baseBranch())
			.replace("{fileCount}", String.valueOf(pr.files().size()))
			.replace("{description}", pr.description() != null ? pr.description() : "(no description)")
			.replace("{fileSummary}", PromptHelper.fileSummary(pr))
			.replace("{diff}", PromptHelper.diff(pr))
			.replace("{labels}", String.join(", ", pr.labels()));
	}

	private static String loadTemplate(String resource) {
		try (InputStream is = AssessCodeQualityStep.class.getClassLoader().getResourceAsStream(resource)) {
			if (is == null) {
				return "Analyze this PR for code quality.";
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			return "Analyze this PR for code quality.";
		}
	}

	@Override
	public Class<?> inputType() {
		return PrContext.class;
	}

	@Override
	public Class<?> outputType() {
		return AssessmentResult.class;
	}

}
