package com.tuvium.prreview.model;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Complete context for a pull request under review. Assembled by FetchPrContext from
 * multiple GitHub API calls.
 *
 * @param number PR number
 * @param title PR title
 * @param description PR body (null if empty)
 * @param author PR author login
 * @param labels PR labels
 * @param state open, closed, or merged
 * @param baseBranch target branch (e.g., main)
 * @param headBranch source branch
 * @param files changed files with diffs
 * @param comments PR comments
 * @param reviews PR reviews
 * @param linkedIssues linked issues
 */
public record PrContext(int number, String title, @Nullable String description, String author, List<String> labels,
		String state, String baseBranch, String headBranch, List<FileChange> files, List<Comment> comments,
		List<Review> reviews, List<Issue> linkedIssues) {

	public PrContext {
		labels = List.copyOf(labels);
		files = List.copyOf(files);
		comments = List.copyOf(comments);
		reviews = List.copyOf(reviews);
		linkedIssues = List.copyOf(linkedIssues);
	}

}
