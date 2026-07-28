package io.github.markpollack.prreview.steps;

import io.github.markpollack.prreview.model.FixDecision;
import io.github.markpollack.prreview.model.FixOutcome;
import io.github.markpollack.workflow.flows.v3.Step;

import org.springframework.stereotype.Component;

/**
 * Decides whether the pipeline attempts an AI fix for failing tests.
 *
 * <p>
 * The predicate is structural — the branch rebased, the conflicts are simple enough to
 * work in, and the build actually failed. Whether attempting fixes is <em>allowed</em> is
 * not asked here: that is the node's {@code config}
 * ({@link io.github.markpollack.prreview.model.FixPolicy}), which rides {@code specHash}
 * and is visible to whoever approves the artifact.
 *
 * <p>
 * In v1 this predicate lived inside {@code FixAndRetestStep} as a private method reading
 * three values out of the ambient context, so nothing downstream could see that a branch
 * was being taken, let alone on what. {@code FixAndRetestStep} now delegates to this
 * class, which is why there is one predicate and not two.
 */
@Component
public class ShouldAttemptFixStep implements Step<FixDecision, FixOutcome> {

	@Override
	public String name() {
		return "should-attempt-fix";
	}

	@Override
	public FixOutcome execute(FixDecision decision) {
		return decision.rebase().success() && !decision.conflicts().hasComplexConflicts() && !decision.build().skipped()
				&& !decision.build().success() ? FixOutcome.FIX : FixOutcome.SKIP;
	}

}
