package io.github.markpollack.prreview.experiment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.jury.TierConfig;
import io.github.markpollack.judge.jury.TierPolicy;

/**
 * Builds a cascaded {@link Jury} from per-tier judges + policies. Ported verbatim from
 * the code-coverage v4 exemplar ({@code experiment-code-coverage-v4}), agent-judge
 * 0.13.0. Tiers are sorted by number; each becomes a {@link SimpleJury} (consensus
 * voting) wrapped in a {@link TierConfig}, assembled into a {@link CascadedJury}.
 *
 * <p>
 * For the agent-experiment reviewer the tiers are: tier 0 = {@code BuildJudge}
 * ({@link TierPolicy#REJECT_ON_ANY_FAIL} — a broken build short-circuits before the LLM
 * tier), tier 1 = {@code QualityJudge} ({@link TierPolicy#FINAL_TIER}). The spring-ai
 * version/backport tiers are intentionally absent.
 */
public class JuryFactory {

	private final Map<Integer, List<Judge>> tierJudges;

	private final Map<Integer, TierPolicy> tierPolicies;

	public JuryFactory(Map<Integer, List<Judge>> tierJudges, Map<Integer, TierPolicy> tierPolicies) {
		this.tierJudges = tierJudges;
		this.tierPolicies = tierPolicies;
	}

	public Jury build() {
		List<TierConfig> tiers = new ArrayList<>();

		for (var entry : tierJudges.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
			int tierNum = entry.getKey();
			List<Judge> judges = entry.getValue();
			TierPolicy policy = tierPolicies.getOrDefault(tierNum, TierPolicy.FINAL_TIER);

			SimpleJury.Builder juryBuilder = SimpleJury.builder().votingStrategy(new ConsensusStrategy());
			for (Judge judge : judges) {
				juryBuilder.judge(judge);
			}
			SimpleJury tierJury = juryBuilder.build();
			tiers.add(new TierConfig("tier-" + tierNum, tierJury, policy));
		}

		CascadedJury.Builder cascadeBuilder = CascadedJury.builder();
		for (TierConfig tier : tiers) {
			cascadeBuilder.tier(tier.name(), tier.jury(), tier.policy());
		}
		return cascadeBuilder.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final Map<Integer, List<Judge>> tierJudges = new TreeMap<>();

		private final Map<Integer, TierPolicy> tierPolicies = new HashMap<>();

		public Builder addJudge(int tier, Judge judge) {
			tierJudges.computeIfAbsent(tier, k -> new ArrayList<>()).add(judge);
			return this;
		}

		public Builder tierPolicy(int tier, TierPolicy policy) {
			tierPolicies.put(tier, policy);
			return this;
		}

		public JuryFactory build() {
			return new JuryFactory(tierJudges, tierPolicies);
		}

	}

}
