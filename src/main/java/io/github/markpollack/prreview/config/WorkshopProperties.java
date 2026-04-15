package io.github.markpollack.prreview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Workshop-specific configuration.
 *
 * @param defaultPr default PR number for workshop demo
 * @param skipAi skip AI assessment steps (for testing deterministic pipeline)
 * @param fixTests enable AI fix-tests step when tests fail after rebase
 * @param journalDir directory for journal output files
 * @param repoDir local clone of the target repository for rebase and test execution
 * @param useDsl use DSL-based workflow instead of manual orchestrator
 */
@ConfigurationProperties(prefix = "workshop")
public record WorkshopProperties(int defaultPr, boolean skipAi, boolean fixTests, String journalDir, String repoDir,
		boolean useDsl) {
}
