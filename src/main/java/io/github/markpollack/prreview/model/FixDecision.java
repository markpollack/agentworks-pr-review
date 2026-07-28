package io.github.markpollack.prreview.model;

/**
 * What the fix/skip decision reads. All three values are node outputs; in v1 the same
 * three were read from the ambient context inside a composite step, where nothing
 * downstream could see that the decision depended on them.
 *
 * @param rebase whether the branch rebased cleanly
 * @param conflicts whether the conflicts are simple enough to attempt a fix
 * @param build the build the fix would be repairing
 */
public record FixDecision(RebaseResult rebase, ConflictReport conflicts, BuildResult build) {
}
