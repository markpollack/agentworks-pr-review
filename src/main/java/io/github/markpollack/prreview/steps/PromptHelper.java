package io.github.markpollack.prreview.steps;

import java.util.stream.Collectors;

import io.github.markpollack.prreview.model.FileChange;
import io.github.markpollack.prreview.model.PrContext;

/**
 * Shared utilities for rendering prompt template placeholders from PR context.
 */
final class PromptHelper {

	private static final int MAX_DIFF_LENGTH = 8_000;

	private PromptHelper() {
	}

	/** One-line-per-file summary: filename (status, +additions/-deletions). */
	static String fileSummary(PrContext pr) {
		return pr.files()
			.stream()
			.map(f -> String.format("- %s (%s, +%d/-%d)", f.filename(), f.status(), f.additions(), f.deletions()))
			.collect(Collectors.joining("\n"));
	}

	/** Concatenated diffs, truncated to keep prompt within reasonable bounds. */
	static String diff(PrContext pr) {
		StringBuilder sb = new StringBuilder();
		for (FileChange file : pr.files()) {
			if (file.patch() == null) {
				continue;
			}
			sb.append("### ").append(file.filename()).append("\n");
			sb.append(file.patch()).append("\n\n");
		}
		String full = sb.toString();
		if (full.length() <= MAX_DIFF_LENGTH) {
			return full;
		}
		return full.substring(0, MAX_DIFF_LENGTH) + "\n... (diff truncated)";
	}

}
