package com.tuvium.prreview.model;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Result of rebasing a PR branch onto main.
 *
 * @param success whether the rebase completed cleanly
 * @param branch branch name
 * @param conflictFiles files with conflicts (empty if clean rebase)
 * @param errorMessage error details if rebase failed (null on success)
 */
public record RebaseResult(boolean success, String branch, List<String> conflictFiles, @Nullable String errorMessage) {

	public RebaseResult {
		conflictFiles = List.copyOf(conflictFiles);
	}

	public static RebaseResult clean(String branch) {
		return new RebaseResult(true, branch, List.of(), null);
	}

	public static RebaseResult conflict(String branch, List<String> conflictFiles) {
		return new RebaseResult(false, branch, conflictFiles, "Rebase conflict in " + conflictFiles.size() + " files");
	}

}
