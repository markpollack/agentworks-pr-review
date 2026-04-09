package com.tuvium.prreview.model;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Result of running Maven tests on affected modules.
 *
 * @param success whether the build and tests passed
 * @param skipped true if skipped due to complex conflicts
 * @param modules Maven modules tested
 * @param output build output (truncated; null if skipped)
 * @param durationMs build duration in milliseconds
 */
public record BuildResult(boolean success, boolean skipped, List<String> modules, @Nullable String output,
		long durationMs) {

	public BuildResult {
		modules = List.copyOf(modules);
	}

	public static BuildResult buildSkipped() {
		return new BuildResult(false, true, List.of(), null, 0);
	}

}
