package io.github.markpollack.prreview.model;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Result of an AI-powered test fix attempt.
 *
 * @param attempted whether the fix was attempted (false if tests already pass)
 * @param fixed whether the AI successfully fixed the tests
 * @param filesChanged list of files modified by the fix
 * @param summary description of what was fixed or why it couldn't be fixed
 */
public record FixResult(boolean attempted, boolean fixed, List<String> filesChanged, @Nullable String summary) {

	public FixResult {
		filesChanged = List.copyOf(filesChanged);
	}

	public static FixResult notNeeded() {
		return new FixResult(false, false, List.of(), "Tests already pass");
	}

}
