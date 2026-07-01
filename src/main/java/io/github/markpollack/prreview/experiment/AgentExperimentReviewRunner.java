package io.github.markpollack.prreview.experiment;

import java.nio.file.Path;

import io.github.markpollack.workflow.core.AgentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Steward invocation for the agent-experiment reviewer — the Spring
 * {@link CommandLineRunner} that replaces the retired no-Spring
 * {@code PrReviewExperimentRunner}. Active only under the {@code agent-experiment}
 * profile, so it does not run alongside the spring-ai {@code PrReviewRunner}.
 *
 * <p>
 * Run:
 * {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=agent-experiment -Dspring-boot.run.arguments=<prNumber>}.
 * From inside a Claude Code session wrap with {@code ~/scripts/claude-run.sh} so the
 * nested agent escapes the parent process tree.
 */
@Component
@Profile("agent-experiment")
public class AgentExperimentReviewRunner implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(AgentExperimentReviewRunner.class);

	private static final int DEFAULT_PR = 1;

	private final PrReviewExperimentWorkflow reviewer;

	public AgentExperimentReviewRunner(PrReviewExperimentWorkflow reviewer) {
		this.reviewer = reviewer;
	}

	@Override
	public void run(String... args) {
		int prNumber = parsePrNumber(args);
		logger.info("Reviewing {} PR #{}", this.reviewer.repo(), prNumber);
		Path report = this.reviewer.handle(AgentContext.create(), prNumber);
		logger.info("Report written to {}", report.toAbsolutePath());
	}

	/**
	 * Parses the PR number from the command-line args (first bare, non-flag integer);
	 * falls back to {@link #DEFAULT_PR}.
	 */
	static int parsePrNumber(String[] args) {
		for (String arg : args) {
			if (!arg.startsWith("--")) {
				try {
					return Integer.parseInt(arg);
				}
				catch (NumberFormatException ignored) {
				}
			}
		}
		return DEFAULT_PR;
	}

}
