package io.github.markpollack.prreview;

import java.nio.file.Path;

import io.github.markpollack.prreview.config.WorkshopProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Entry point for the PR review pipeline. Supports two modes:
 *
 * <ul>
 * <li>{@code --check} — run pre-flight checks only</li>
 * <li>(default) — execute the full PR review pipeline</li>
 * </ul>
 *
 * <p>
 * Override the target PR with {@code --workshop.default-pr=1234}. Override the repo with
 * {@code --github.repo=owner/repo}.
 */
@Component
public class PrReviewRunner implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(PrReviewRunner.class);

	private final PrReviewWorkflow workflow;

	private final PreflightCheck preflightCheck;

	private final WorkshopProperties workshopProperties;

	public PrReviewRunner(PrReviewWorkflow workflow, PreflightCheck preflightCheck,
			WorkshopProperties workshopProperties) {
		this.workflow = workflow;
		this.preflightCheck = preflightCheck;
		this.workshopProperties = workshopProperties;
	}

	@Override
	public void run(String... args) throws Exception {
		if (hasFlag(args, "--check")) {
			logger.info("Running pre-flight checks...");
			boolean ready = this.preflightCheck.runAndReport();
			if (!ready) {
				System.exit(1);
			}
			return;
		}

		// Determine PR number: from args or default
		int prNumber = parsePrNumber(args);
		if (prNumber <= 0) {
			prNumber = this.workshopProperties.defaultPr();
		}

		logger.info("Starting PR review for PR #{}", prNumber);
		Path report = this.workflow.execute(prNumber);
		logger.info("Review complete. Report: {}", report.toAbsolutePath());
	}

	private static boolean hasFlag(String[] args, String flag) {
		for (String arg : args) {
			if (flag.equals(arg)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Parses PR number from command line args. Accepts bare number as first non-flag
	 * argument.
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
		return -1;
	}

}
