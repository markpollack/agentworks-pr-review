package com.tuvium.prreview.judges;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.tuvium.prreview.model.FileChange;
import com.tuvium.prreview.model.PrContext;
import org.springaicommunity.judge.Judge;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Check;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;
import org.springaicommunity.judge.score.BooleanScore;

import org.springframework.stereotype.Component;

/**
 * T1 deterministic judge — scans PR diffs for known version migration anti-patterns.
 *
 * <p>
 * Pure pattern matching on diff content, no AI. Catches Boot 3→4 migration issues like
 * wrong Jackson package, deprecated annotations, and old API usage. Each match is
 * reported as a {@link Check} with the file and pattern details.
 *
 * <p>
 * PASS if no patterns matched; FAIL if any found (blocks LLM spend on broken code).
 */
@Component
public class VersionPatternJudge implements Judge {

	/** Metadata key for {@link PrContext} — provides file patches for scanning. */
	public static final String PR_CONTEXT = "prContext";

	static final List<VersionPattern> PATTERNS = List.of(
			new VersionPattern("javax-imports", Pattern.compile("^\\+.*import\\s+javax\\.", Pattern.MULTILINE),
					"javax.* import — should be jakarta.* in Boot 3+"),
			new VersionPattern("jackson-2x", Pattern.compile("^\\+.*com\\.fasterxml\\.jackson", Pattern.MULTILINE),
					"Jackson 2.x package — Boot 4 uses tools.jackson.databind"),
			new VersionPattern("mock-bean",
					Pattern.compile("^\\+.*@MockBean|^\\+.*import.*\\.boot\\.test\\.mock\\.mockito\\.MockBean",
							Pattern.MULTILINE),
					"@MockBean — Boot 4 uses @MockitoBean"),
			new VersionPattern("mock-mvc-legacy",
					Pattern.compile("^\\+.*MockMvcRequestBuilders|^\\+.*import.*\\.test\\.web\\.servlet\\.request",
							Pattern.MULTILINE),
					"MockMvcRequestBuilders — Boot 4 prefers MockMvcTester"),
			new VersionPattern("web-security-configurer",
					Pattern.compile("^\\+.*WebSecurityConfigurerAdapter", Pattern.MULTILINE),
					"WebSecurityConfigurerAdapter — removed in Spring Security 6+"));

	@Override
	public Judgment judge(JudgmentContext context) {
		PrContext prContext = extract(context, PR_CONTEXT, PrContext.class);

		if (prContext == null) {
			return Judgment.builder()
				.score(new BooleanScore(true))
				.status(JudgmentStatus.PASS)
				.reasoning("No PR context available — skipping version pattern check")
				.build();
		}

		List<Check> checks = new ArrayList<>();

		for (FileChange file : prContext.files()) {
			if (file.patch() == null || file.patch().isEmpty()) {
				continue;
			}
			scanFile(file, checks);
		}

		boolean hasFindings = checks.stream().anyMatch(c -> !c.passed());
		long findings = checks.stream().filter(c -> !c.passed()).count();

		String reasoning;
		if (hasFindings) {
			reasoning = "Version pattern judge: " + findings + " anti-pattern" + (findings == 1 ? "" : "s")
					+ " found in PR diff";
		}
		else {
			reasoning = "Version pattern judge: no migration anti-patterns detected";
		}

		return Judgment.builder()
			.score(new BooleanScore(!hasFindings))
			.status(hasFindings ? JudgmentStatus.FAIL : JudgmentStatus.PASS)
			.reasoning(reasoning)
			.checks(checks)
			.build();
	}

	private static void scanFile(FileChange file, List<Check> checks) {
		String patch = file.patch();
		for (VersionPattern vp : PATTERNS) {
			if (vp.pattern().matcher(patch).find()) {
				checks.add(Check.fail(vp.id(), file.filename() + ": " + vp.description()));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T extract(JudgmentContext context, String key, Class<T> type) {
		Object value = context.metadata().get(key);
		if (value == null) {
			return null;
		}
		return type.cast(value);
	}

	record VersionPattern(String id, Pattern pattern, String description) {
	}

}
