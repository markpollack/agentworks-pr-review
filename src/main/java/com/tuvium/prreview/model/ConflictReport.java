package com.tuvium.prreview.model;

import java.util.List;

/**
 * Result of conflict detection after a rebase attempt.
 *
 * @param conflicts per-file conflict details
 * @param hasComplexConflicts true if any conflict is classified as COMPLEX
 * @param summary human-readable summary (never empty)
 */
public record ConflictReport(List<ConflictFile> conflicts, boolean hasComplexConflicts, String summary) {

	public ConflictReport {
		conflicts = List.copyOf(conflicts);
	}

	public static ConflictReport clean() {
		return new ConflictReport(List.of(), false, "Clean rebase, no conflicts");
	}

}
