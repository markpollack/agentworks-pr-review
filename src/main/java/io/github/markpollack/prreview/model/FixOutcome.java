package io.github.markpollack.prreview.model;

/**
 * The closed outcome set of the fix decision — the compiler's list, not a string list. A
 * decision node's {@code outcomes} are read off this enum, so an arm the graph does not
 * route is a build failure rather than a validation one.
 */
public enum FixOutcome {

	FIX, SKIP

}
