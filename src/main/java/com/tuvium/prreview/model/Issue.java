package com.tuvium.prreview.model;

import java.util.List;

/**
 * A GitHub issue linked to a pull request.
 *
 * @param number issue number
 * @param title issue title
 * @param labels issue labels
 */
public record Issue(int number, String title, List<String> labels) {

	public Issue {
		labels = List.copyOf(labels);
	}

}
