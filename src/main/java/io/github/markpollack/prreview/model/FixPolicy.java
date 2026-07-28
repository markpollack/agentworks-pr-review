package io.github.markpollack.prreview.model;

/**
 * The one thing the fix decision's behaviour turns on, as the artifact carries it.
 *
 * <p>
 * In v1 this was {@code workshop.fix-tests}, a Spring property read inside the composite
 * step: an operator could flip whether a pipeline attempts AI repairs and the approved
 * artifact would not change by one byte. As a node {@code config} it rides
 * {@code specHash}, so flipping it is a new spec.
 *
 * <p>
 * The two factories are not ceremony — {@code new FixPolicy(true)} at a call site is
 * boolean-blind, and Java has no named arguments.
 *
 * @param fixTestsEnabled whether the pipeline may attempt an AI fix for failing tests
 */
public record FixPolicy(boolean fixTestsEnabled) {

	public static FixPolicy attemptingFixes() {
		return new FixPolicy(true);
	}

	public static FixPolicy skippingFixes() {
		return new FixPolicy(false);
	}

}
