package io.github.markpollack.prreview.steps;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import io.github.markpollack.prreview.model.Classification;
import io.github.markpollack.prreview.model.ConflictFile;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.workflow.flows.AgentContext;
import io.github.markpollack.workflow.flows.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Classifies conflicts from a failed rebase as SIMPLE or COMPLEX.
 *
 * <p>
 * SIMPLE conflicts (whitespace, import ordering, version bumps) are likely
 * auto-resolvable. COMPLEX conflicts (logic changes, overlapping edits, structural
 * refactors) need human review.
 */
@Component
public class ConflictDetectionStep implements Step<RebaseResult, ConflictReport> {

	private static final Logger logger = LoggerFactory.getLogger(ConflictDetectionStep.class);

	/** File patterns that typically produce simple, auto-resolvable conflicts. */
	private static final List<Pattern> SIMPLE_PATTERNS = List.of(Pattern.compile("pom\\.xml$"),
			Pattern.compile("build\\.gradle(\\.kts)?$"), Pattern.compile("gradle\\.properties$"),
			Pattern.compile("\\.properties$"), Pattern.compile("package-info\\.java$"));

	@Override
	public String name() {
		return "detect-conflicts";
	}

	@Override
	public ConflictReport execute(AgentContext ctx, RebaseResult input) {
		if (input.success()) {
			logger.info("Clean rebase, no conflicts to classify");
			return ConflictReport.clean();
		}

		List<String> conflictPaths = input.conflictFiles();
		logger.info("Classifying {} conflicted files", conflictPaths.size());

		List<ConflictFile> classified = new ArrayList<>();
		boolean hasComplex = false;

		for (String path : conflictPaths) {
			Classification classification = classify(path);
			String description = describeConflict(path, classification);
			classified.add(new ConflictFile(path, classification, description));

			if (classification == Classification.COMPLEX) {
				hasComplex = true;
			}
			logger.debug("  {} → {} ({})", path, classification, description);
		}

		String summary = buildSummary(classified, hasComplex);
		logger.info("Conflict report: {}", summary);

		return new ConflictReport(classified, hasComplex, summary);
	}

	/**
	 * Classifies a file path as SIMPLE or COMPLEX based on filename patterns.
	 * <p>
	 * Build files (pom.xml, build.gradle), property files, and package-info.java are
	 * classified as SIMPLE. Everything else is COMPLEX.
	 */
	static Classification classify(String path) {
		for (Pattern pattern : SIMPLE_PATTERNS) {
			if (pattern.matcher(path).find()) {
				return Classification.SIMPLE;
			}
		}
		return Classification.COMPLEX;
	}

	private static String describeConflict(String path, Classification classification) {
		if (classification == Classification.SIMPLE) {
			if (path.endsWith("pom.xml") || path.endsWith("build.gradle") || path.endsWith("build.gradle.kts")) {
				return "version bump or dependency conflict";
			}
			if (path.endsWith(".properties")) {
				return "property value conflict";
			}
			return "likely auto-resolvable formatting conflict";
		}
		return "overlapping code changes requiring human review";
	}

	private static String buildSummary(List<ConflictFile> conflicts, boolean hasComplex) {
		long simple = conflicts.stream().filter(c -> c.classification() == Classification.SIMPLE).count();
		long complex = conflicts.size() - simple;

		StringBuilder sb = new StringBuilder();
		sb.append(conflicts.size()).append(" conflict");
		if (conflicts.size() != 1) {
			sb.append("s");
		}
		sb.append(": ");

		List<String> parts = new ArrayList<>();
		if (simple > 0) {
			parts.add(simple + " simple (auto-resolvable)");
		}
		if (complex > 0) {
			parts.add(complex + " complex (needs human review)");
		}
		sb.append(String.join(", ", parts));

		return sb.toString();
	}

	@Override
	public Class<?> inputType() {
		return RebaseResult.class;
	}

	@Override
	public Class<?> outputType() {
		return ConflictReport.class;
	}

}
