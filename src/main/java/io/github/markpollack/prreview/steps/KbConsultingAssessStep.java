package io.github.markpollack.prreview.steps;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.github.markpollack.prreview.config.ReviewProperties;
import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.core.Description;
import io.github.markpollack.workflow.core.StepName;
import io.github.markpollack.workflow.flows.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.markpollack.agents.client.AgentClient;
import io.github.markpollack.agents.client.AgentClientResponse;
import io.github.markpollack.judge.result.JudgmentStatus;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * KB-consulting code-quality assessment (DD-11 / DD-15) — the agent-experiment reviewer's
 * assess step, and the reusable customization point for any KB-grounded reviewer.
 *
 * <p>
 * Unlike the spring-ai-agnostic {@link AssessCodeQualityStep} (which it deliberately
 * leaves untouched), this step's prompt <strong>instructs the agent to READ the
 * configured KB briefs by path</strong> ({@code review.kb.briefs}) before assessing — the
 * KB is not inlined (it is large and stays the single source of truth; deeper concept
 * files are pulled just-in-time by the same read-by-path mechanism). The prompt also
 * bakes in the anti-over-mapping discipline (most PRs have no touchpoint; an optional add
 * is not a flaw; ground every claim on a diff symbol AND a concept slug; live vs latent;
 * never conflate {@code V(EXPLORE)} with {@code J}).
 *
 * <p>
 * This is a generic, profile-agnostic building block: it reads its brief list from
 * {@link ReviewProperties}, so any repo's reviewer profile reuses it by pointing
 * {@code review.kb.briefs} at that repo's briefs. It publishes its result under
 * {@link AssessCodeQualityStep#QUALITY_ASSESSMENT}, so it is a drop-in for the same
 * downstream quality gate / mapper.
 */
@Component
@Qualifier("kb-consulting-assess")
@StepName("kb-consulting-assess")
@Description("KB-consulting code quality assessment — instructs the agent to read the configured KB briefs before assessing")
public class KbConsultingAssessStep implements Step<PrContext, AssessmentResult> {

	private static final String JUDGE_NAME = "kb-consulting-quality";

	private static final Logger logger = LoggerFactory.getLogger(KbConsultingAssessStep.class);

	private static final String PROMPT_TEMPLATE = loadTemplate("prompts/kb-consulting-assessment.md");

	private final AgentClient agentClient;

	private final ReviewProperties reviewProperties;

	private AgentClientResponse lastResponse;

	public KbConsultingAssessStep(AgentClient agentClient, ReviewProperties reviewProperties) {
		this.agentClient = agentClient;
		this.reviewProperties = reviewProperties;
	}

	public AgentClientResponse lastResponse() {
		return this.lastResponse;
	}

	@Override
	public String name() {
		return "kb-consulting-assess";
	}

	@Override
	public AssessmentResult execute(AgentContext ctx, PrContext input) {
		List<String> briefs = this.reviewProperties.kb().briefs();
		logger.info("Running KB-consulting assessment for PR #{} (consulting {} brief(s))", input.number(),
				briefs.size());

		String prompt = renderPrompt(input, briefs);
		try {
			AgentClientResponse response = this.agentClient.run(prompt);
			this.lastResponse = response;
			String result = response.getResult();
			logger.info("KB-consulting assessment complete for PR #{}", input.number());
			return AssessmentParser.parse(JUDGE_NAME, result);
		}
		catch (Exception ex) {
			logger.error("KB-consulting assessment failed for PR #{}: {}", input.number(), ex.getMessage());
			return new AssessmentResult(JUDGE_NAME, JudgmentStatus.ERROR, 0.0, "Assessment failed: " + ex.getMessage(),
					List.of());
		}
	}

	@Override
	public AgentContext updateContext(AgentContext ctx, AssessmentResult output) {
		// Publish under the shared quality key so the downstream quality gate/mapper
		// picks
		// it up unchanged — a drop-in replacement for AssessCodeQualityStep.
		AgentContext.Builder builder = ctx.mutate().with(AssessCodeQualityStep.QUALITY_ASSESSMENT, output);
		if (this.lastResponse != null) {
			builder.with(AssessCodeQualityStep.QUALITY_RESPONSE, this.lastResponse);
		}
		return builder.build();
	}

	static String renderPrompt(PrContext pr, List<String> briefs) {
		return PROMPT_TEMPLATE.replace("{briefs}", renderBriefs(briefs))
			.replace("{number}", String.valueOf(pr.number()))
			.replace("{title}", pr.title())
			.replace("{author}", pr.author())
			.replace("{baseBranch}", pr.baseBranch())
			.replace("{fileCount}", String.valueOf(pr.files().size()))
			.replace("{description}", pr.description() != null ? pr.description() : "(no description)")
			.replace("{fileSummary}", PromptHelper.fileSummary(pr))
			.replace("{diff}", PromptHelper.diff(pr))
			.replace("{labels}", String.join(", ", pr.labels()));
	}

	private static String renderBriefs(List<String> briefs) {
		if (briefs == null || briefs.isEmpty()) {
			return "(no briefs configured — assess on general code-quality grounds only)";
		}
		StringBuilder sb = new StringBuilder();
		for (String brief : briefs) {
			sb.append("- ").append(brief).append("\n");
		}
		return sb.toString().stripTrailing();
	}

	private static String loadTemplate(String resource) {
		try (InputStream is = KbConsultingAssessStep.class.getClassLoader().getResourceAsStream(resource)) {
			if (is == null) {
				return "Read the configured KB briefs by path:\n{briefs}\n\nThen assess PR #{number}: {title}.";
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			return "Read the configured KB briefs by path:\n{briefs}\n\nThen assess PR #{number}: {title}.";
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
