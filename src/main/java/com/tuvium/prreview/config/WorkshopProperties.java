package com.tuvium.prreview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Workshop-specific configuration.
 *
 * @param defaultPr default PR number for workshop demo
 * @param skipAi skip AI assessment steps (for testing deterministic pipeline)
 * @param journalDir directory for journal output files
 */
@ConfigurationProperties(prefix = "workshop")
public record WorkshopProperties(int defaultPr, boolean skipAi, String journalDir) {
}
