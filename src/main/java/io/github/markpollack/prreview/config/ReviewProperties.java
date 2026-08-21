package io.github.markpollack.prreview.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Review-target + KB-consult configuration — the per-repo customization surface (DD-15).
 *
 * <p>
 * A reviewer for a new repository is <em>configured</em>, not forked. A profile (or
 * {@code application-<profile>.yml}) sets {@code review.target.*} (which repo to review
 * and the local clone to rebase/test in), {@code review.kb.briefs} (the knowledge-base
 * briefs the assess step instructs the agent to read by path), and
 * {@code review.max-cost-usd} (the per-run cost ceiling). Bound globally via
 * {@code @ConfigurationPropertiesScan}, so it is always present; only the values change
 * per profile.
 *
 * @param target the repository under review
 * @param kb the knowledge bases to consult at assess time
 * @param maxCostUsd per-run cost ceiling in USD (0 → use the reviewer's default)
 * @param autoResolve whether to AI-resolve rebase conflicts (DD-7); when false, a
 * conflict escalates to the human path (report only). Defaults to false when unset — the
 * agent-experiment profile turns it on.
 */
@ConfigurationProperties(prefix = "review")
public record ReviewProperties(Target target, Kb kb, double maxCostUsd, boolean autoResolve) {

	public ReviewProperties {
		if (target == null) {
			target = new Target(null, null);
		}
		if (kb == null) {
			kb = new Kb(List.of());
		}
	}

	/**
	 * The repository under review.
	 *
	 * @param repo {@code owner/name} of the repository (GitHub API + report header)
	 * @param repoDir local clone used for rebase/test execution and the agent working
	 * directory
	 */
	public record Target(String repo, String repoDir) {
	}

	/**
	 * The knowledge bases the assess step instructs the agent to consult.
	 *
	 * @param briefs file paths of the KB briefs the agent reads (by path) before
	 * assessing
	 */
	public record Kb(List<String> briefs) {
		public Kb {
			briefs = (briefs == null) ? List.of() : List.copyOf(briefs);
		}
	}

}
