package io.github.markpollack.prreview.experiment;

import java.nio.file.Path;

import io.github.markpollack.prreview.config.ReviewProperties;
import io.github.markpollack.prreview.judges.BuildJudge;
import io.github.markpollack.prreview.judges.QualityJudge;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.GenerateReportStep;
import io.github.markpollack.prreview.steps.KbConsultingAssessStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.ResolveConflictsStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.agents.claude.ClaudeAgentModel;
import io.github.markpollack.agents.client.AgentClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * The agent-experiment reviewer — assembled entirely from shared beans by overriding /
 * providing a handful of beans under the {@code agent-experiment} profile (DD-15). This
 * is the <em>worked example</em> of the product's customization blueprint: a reviewer for
 * a new repository is a profile like this one, not a fork.
 *
 * <p>
 * What this profile customizes (the whole customization surface):
 * <ul>
 * <li>{@code review.*} ({@link ReviewProperties}) — the target repo + the KB briefs + the
 * cost ceiling, set in {@code application-agent-experiment.yml} (no code).</li>
 * <li>a {@link Primary @Primary} trace-wired {@link AgentClient} — so the KB-consulting
 * assess step records its JSONL trace and runs rooted in the target clone; this overrides
 * the default {@code JournalConfig} client for this profile (a bean override).</li>
 * <li>the reviewer assembly — shared deterministic steps + {@link BuildJudge} + a
 * backport-free {@link QualityJudge} + {@link KbConsultingAssessStep}, mapper-fed
 * {@code JudgeGate}s, dropping the spring-ai version/backport tiers (DD-9).</li>
 * </ul>
 *
 * <p>
 * <strong>Commonality note (input for the generic-agent extraction).</strong> Two beans
 * resist clean override here, both because the spring-ai V1 reviewer annotates its judges
 * with {@code @Component} (so they are <em>unconditionally</em> registered, defeating a
 * customizing profile's {@code @ConditionalOnMissingBean}):
 * <ul>
 * <li>{@link QualityJudge} hardwires {@code requireBackport=true} via its
 * {@code @Component} default constructor; this profile needs {@code false} (DD-13), so we
 * construct the variant directly rather than reuse the component bean.</li>
 * <li>{@link BuildJudge}/{@link QualityJudge} are injected by concrete type, not an
 * interface, so a profile cannot transparently swap them yet.</li>
 * </ul>
 * The generic PR-review agent should publish its judges as {@code @Bean}
 * {@code @ConditionalOnMissingBean} factory methods (not {@code @Component}) and inject
 * them by interface, so a customer profile can override a judge by simply declaring one.
 */
@Configuration(proxyBeanMethods = false)
@Profile("agent-experiment")
public class AgentExperimentReviewerConfig {

	private static final Path TRACE_DIR = Path.of("traces");

	/**
	 * Trace-wired client for the assess step (DD-14): records the call's JSONL trace into
	 * the journal and roots the agent in the target clone. Marked
	 * {@link Primary @Primary} so it wins over the default {@code JournalConfig}
	 * {@code AgentClient} under this profile — the bean-override customization point.
	 */
	@Bean
	@Primary
	AgentClient agentExperimentAgentClient(ReviewProperties reviewProperties) {
		Path repoDir = Path.of(reviewProperties.target().repoDir());
		ClaudeAgentModel model = ClaudeAgentModel.builder().workingDirectory(repoDir).traceDir(TRACE_DIR).build();
		return AgentClient.create(model);
	}

	/**
	 * Assembles the agent-experiment reviewer from the shared, Spring-managed building
	 * blocks. The deterministic steps, {@link BuildJudge}, {@link GenerateReportStep} and
	 * {@link KbConsultingAssessStep} are injected (overridable beans); the backport-free
	 * {@link QualityJudge} (DD-13) is constructed here — see the class javadoc for why
	 * the shared {@code @Component QualityJudge} cannot be reused as-is.
	 */
	@Bean
	PrReviewExperimentWorkflow agentExperimentReviewer(FetchPrContextStep fetchPrContext, RebaseStep rebaseStep,
			ConflictDetectionStep conflictDetection, RunTestsStep runTests, ResolveConflictsStep resolveConflicts,
			KbConsultingAssessStep kbConsultingAssess, BuildJudge buildJudge, GenerateReportStep generateReport,
			AgentClient agentExperimentAgentClient, ReviewProperties reviewProperties) {

		QualityJudge qualityJudge = new QualityJudge(agentExperimentAgentClient, false);

		double maxCost = reviewProperties.maxCostUsd() > 0 ? reviewProperties.maxCostUsd()
				: PrReviewExperimentWorkflow.DEFAULT_MAX_COST_USD;

		return new PrReviewExperimentWorkflow(fetchPrContext, rebaseStep, conflictDetection, runTests, resolveConflicts,
				kbConsultingAssess, buildJudge, qualityJudge, generateReport, reviewProperties.target().repo(),
				maxCost);
	}

}
