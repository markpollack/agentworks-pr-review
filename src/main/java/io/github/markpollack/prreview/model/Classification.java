package io.github.markpollack.prreview.model;

/**
 * Classification of a merge conflict's complexity.
 */
public enum Classification {

	/** Whitespace, import ordering, version bumps — likely auto-resolvable. */
	SIMPLE,

	/** Logic changes, overlapping edits, structural refactors — needs human review. */
	COMPLEX

}
