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
 * AI-powered backport candidacy assessment using AgentClient.
 *
 * <p>
 * Evaluates whether a PR is a good candidate for backporting to maintenance branches.
 * Small, focused fixes with no API changes score well; large features or dependency bumps
 * score poorly.
 */
@Component
public class AssessBackportStep implements Step<PrContext, AssessmentResult> {

	public static final ContextKey<AssessmentResult> BACKPORT_ASSESSMENT = ContextKey.of("backportAssessment",
			AssessmentResult.class);

	private static final Logger logger = LoggerFactory.getLogger(AssessBackportStep.class);

	private static final String PROMPT_TEMPLATE = loadTemplate("prompts/backport-assessment.md");

	private final AgentClient agentClient;

	private AgentClientResponse lastResponse;

	public AssessBackportStep(AgentClient agentClient) {
		this.agentClient = agentClient;
	}

	public AgentClientResponse lastResponse() {
		return this.lastResponse;
	}

	@Override
	public String name() {
		return "assess-backport";
	}

	@Override
	public AssessmentResult execute(AgentContext ctx, PrContext input) {
		logger.info("Running backport assessment for PR #{}", input.number());

		String prompt = renderPrompt(input);
		try {
			AgentClientResponse response = this.agentClient.run(prompt);
			this.lastResponse = response;
			String result = response.getResult();
			logger.info("Backport assessment complete for PR #{}", input.number());
			return AssessmentParser.parse("backport", result);
		}
		catch (Exception ex) {
			logger.error("Backport assessment failed for PR #{}: {}", input.number(), ex.getMessage());
			return new AssessmentResult("backport", JudgmentStatus.ERROR, 0.0, "Assessment failed: " + ex.getMessage(),
					List.of());
		}
	}

	@Override
	public AgentContext updateContext(AgentContext ctx, AssessmentResult output) {
		return ctx.mutate().with(BACKPORT_ASSESSMENT, output).build();
	}

	static String renderPrompt(PrContext pr) {
		return PROMPT_TEMPLATE.replace("{number}", String.valueOf(pr.number()))
			.replace("{title}", pr.title())
			.replace("{author}", pr.author())
			.replace("{labels}", String.join(", ", pr.labels()))
			.replace("{description}", pr.description() != null ? pr.description() : "(no description)")
			.replace("{fileSummary}", PromptHelper.fileSummary(pr))
			.replace("{diff}", PromptHelper.diff(pr));
	}

	private static String loadTemplate(String resource) {
		try (InputStream is = AssessBackportStep.class.getClassLoader().getResourceAsStream(resource)) {
			if (is == null) {
				return "Assess this PR for backport candidacy.";
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			return "Assess this PR for backport candidacy.";
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
