package io.github.markpollack.prreview.steps;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.Classification;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.FileChange;
import io.github.markpollack.prreview.model.FixResult;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.prreview.model.ReviewReport;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.core.Description;
import io.github.markpollack.workflow.core.StepName;
import io.github.markpollack.workflow.flows.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Final step — assembles all pipeline outputs into a markdown report and writes it to
 * disk.
 *
 * <p>
 * Takes a {@link ReviewReport} (assembled by the workflow orchestrator) and renders it
 * using the report template. Returns the path to the generated file.
 */
@Component
@StepName("generate-report")
@Description("Assembles pipeline outputs into markdown and HTML report")
public class GenerateReportStep implements Step<ReviewReport, Path> {

	private static final Logger logger = LoggerFactory.getLogger(GenerateReportStep.class);

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
		.withZone(ZoneId.systemDefault());

	private Path outputDirectory = Path.of("reports");

	public GenerateReportStep outputDirectory(Path dir) {
		this.outputDirectory = dir;
		return this;
	}

	@Override
	public String name() {
		return "generate-report";
	}

	@Override
	public Path execute(AgentContext ctx, ReviewReport report) {
		logger.info("Generating review report for PR #{}", report.prContext().number());

		String markdown = renderMarkdown(report);
		String html = renderHtml(report);

		String baseName = "review-pr-" + report.prContext().number();
		Path mdPath = this.outputDirectory.resolve(baseName + ".md");
		Path htmlPath = this.outputDirectory.resolve(baseName + ".html");
		try {
			Files.createDirectories(mdPath.getParent());
			Files.writeString(mdPath, markdown);
			Files.writeString(htmlPath, html);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to write report to " + this.outputDirectory, ex);
		}

		logger.info("Reports written to {} and {}", mdPath, htmlPath);
		return mdPath;
	}

	@Override
	public Class<?> inputType() {
		return ReviewReport.class;
	}

	@Override
	public Class<?> outputType() {
		return Path.class;
	}

	String renderHtml(ReviewReport report) {
		return HtmlReportRenderer.render(report);
	}

	String renderMarkdown(ReviewReport report) {
		PrContext pr = report.prContext();
		RebaseResult rebase = report.rebaseResult();
		ConflictReport conflicts = report.conflictReport();
		BuildResult build = report.buildResult();

		StringBuilder sb = new StringBuilder();

		// Header
		sb.append("# PR Review Report: #").append(pr.number()).append(" — ").append(pr.title()).append("\n\n");
		sb.append("**Generated**: ").append(TIMESTAMP_FORMAT.format(report.generatedAt())).append("\n");
		sb.append("**Author**: ").append(pr.author());
		sb.append(" | **Branch**: `").append(pr.headBranch()).append("` → `").append(pr.baseBranch()).append("`");
		sb.append(" | **State**: ").append(pr.state()).append("\n\n");
		sb.append("---\n\n");

		// Summary table
		sb.append("## Summary\n\n");
		sb.append("| Phase | Status | Details |\n");
		sb.append("|-------|--------|--------|\n");
		sb.append("| Rebase | ")
			.append(statusEmoji(rebase.success()))
			.append(" | ")
			.append(rebaseSummary(rebase))
			.append(" |\n");
		sb.append("| Conflicts | ")
			.append(statusEmoji(!conflicts.hasComplexConflicts()))
			.append(" | ")
			.append(conflicts.summary())
			.append(" |\n");
		sb.append("| Build & Tests | ")
			.append(buildStatusEmoji(build))
			.append(" | ")
			.append(buildSummary(build))
			.append(" |\n");
		FixResult fix = report.fixResult();
		if (fix != null && fix.attempted()) {
			sb.append("| AI Fix-Tests | ")
				.append(fix.fixed() ? "PASS" : "FAIL")
				.append(" | ")
				.append(fix.summary() != null ? fix.summary() : "No summary")
				.append(" |\n");
		}
		sb.append("\n");

		// Overall verdict
		sb.append("**Overall Verdict**: ").append(overallVerdict(report)).append("\n\n");
		sb.append("---\n\n");

		// Phase 1: Deterministic
		sb.append("## Phase 1: Deterministic Context Gathering\n\n");
		appendPrContext(sb, pr);
		appendRebaseResult(sb, rebase);
		appendConflictReport(sb, conflicts);
		appendBuildResult(sb, build);
		appendFixResult(sb, report.fixResult());

		// Phase 2: Judge Cascade
		sb.append("## Phase 2: Judge Cascade\n\n");
		appendJudgments(sb, report);
		sb.append("---\n\n");

		// Phase 3: AI Assessments
		sb.append("## Phase 3: AI Assessments\n\n");
		appendAssessments(sb, report);
		sb.append("---\n\n");

		// Files changed
		sb.append("## Files Changed\n\n");
		appendFileTable(sb, pr.files());
		sb.append("\n---\n\n");
		sb.append("*Report generated by AgentWorks PR Review Pipeline*\n");

		return sb.toString();
	}

	private void appendPrContext(StringBuilder sb, PrContext pr) {
		sb.append("### PR Context\n\n");
		sb.append("- **Title**: ").append(pr.title()).append("\n");
		int additions = pr.files().stream().mapToInt(FileChange::additions).sum();
		int deletions = pr.files().stream().mapToInt(FileChange::deletions).sum();
		sb.append("- **Files Changed**: ").append(pr.files().size()).append("\n");
		sb.append("- **Lines**: +").append(additions).append(" / -").append(deletions).append("\n");
		sb.append("- **Labels**: ")
			.append(pr.labels().isEmpty() ? "none" : String.join(", ", pr.labels()))
			.append("\n");
		if (!pr.linkedIssues().isEmpty()) {
			String issues = pr.linkedIssues()
				.stream()
				.map(i -> "#" + i.number() + " " + i.title())
				.collect(Collectors.joining(", "));
			sb.append("- **Linked Issues**: ").append(issues).append("\n");
		}
		sb.append("\n");
	}

	private void appendRebaseResult(StringBuilder sb, RebaseResult rebase) {
		sb.append("### Rebase Result\n\n");
		sb.append("- **Status**: ").append(statusEmoji(rebase.success())).append("\n");
		sb.append("- **Branch**: `").append(rebase.branch()).append("`\n");
		if (!rebase.conflictFiles().isEmpty()) {
			sb.append("- **Conflicted files**: ").append(rebase.conflictFiles().size()).append("\n");
			rebase.conflictFiles().forEach(f -> sb.append("  - `").append(f).append("`\n"));
		}
		sb.append("\n");
	}

	private void appendConflictReport(StringBuilder sb, ConflictReport conflicts) {
		sb.append("### Conflict Detection\n\n");
		sb.append("- **Summary**: ").append(conflicts.summary()).append("\n");
		if (!conflicts.conflicts().isEmpty()) {
			sb.append("\n| File | Classification | Description |\n");
			sb.append("|------|---------------|-------------|\n");
			conflicts.conflicts()
				.forEach(c -> sb.append("| `")
					.append(c.path())
					.append("` | ")
					.append(c.classification())
					.append(" | ")
					.append(c.description())
					.append(" |\n"));
		}
		sb.append("\n");
	}

	private void appendFixResult(StringBuilder sb, FixResult fix) {
		if (fix == null || !fix.attempted()) {
			return;
		}
		sb.append("### AI Fix-Tests\n\n");
		sb.append("- **Status**: ").append(fix.fixed() ? "PASS" : "FAIL").append("\n");
		if (fix.summary() != null) {
			sb.append("- **Summary**: ").append(fix.summary()).append("\n");
		}
		if (!fix.filesChanged().isEmpty()) {
			sb.append("- **Files modified**: ").append(fix.filesChanged().size()).append("\n");
			fix.filesChanged().forEach(f -> sb.append("  - `").append(f).append("`\n"));
		}
		sb.append("\n");
	}

	private void appendBuildResult(StringBuilder sb, BuildResult build) {
		sb.append("### Build & Tests\n\n");
		sb.append("- **Status**: ").append(buildStatusEmoji(build)).append("\n");
		if (build.skipped()) {
			sb.append("- Build skipped (complex conflicts detected)\n");
		}
		else {
			sb.append("- **Modules**: ").append(build.modules().isEmpty() ? "all" : String.join(", ", build.modules()));
			sb.append("\n");
			sb.append("- **Duration**: ").append(formatDuration(build.durationMs())).append("\n");
		}
		sb.append("\n---\n\n");
	}

	private void appendJudgments(StringBuilder sb, ReviewReport report) {
		if (report.judgments().isEmpty()) {
			sb.append("No judge verdicts recorded.\n\n");
			return;
		}
		for (var judgment : report.judgments()) {
			sb.append("### ").append(judgment.status()).append("\n\n");
			sb.append("- **Score**: ").append(judgment.score()).append("\n");
			sb.append("- **Reasoning**: ").append(judgment.reasoning()).append("\n");
			if (!judgment.checks().isEmpty()) {
				sb.append("- **Checks**:\n");
				judgment.checks()
					.forEach(c -> sb.append("  - ")
						.append(c.passed() ? "PASS" : "FAIL")
						.append(": ")
						.append(c.name())
						.append(c.message() != null ? " — " + c.message() : "")
						.append("\n"));
			}
			sb.append("\n");
		}
	}

	private void appendAssessments(StringBuilder sb, ReviewReport report) {
		List<AssessmentResult> aiAssessments = report.assessments()
			.stream()
			.filter(a -> !"BuildJudge".equals(a.judgeName()) && !"VersionPatternJudge".equals(a.judgeName()))
			.toList();

		if (aiAssessments.isEmpty()) {
			sb.append("AI assessments were not run (blocked by deterministic gates).\n\n");
			return;
		}

		for (var assessment : aiAssessments) {
			sb.append("### ").append(assessment.judgeName()).append("\n\n");
			sb.append("- **Status**: ").append(assessment.status()).append("\n");
			sb.append("- **Score**: ").append(String.format("%.0f%%", assessment.score() * 100)).append("\n");
			sb.append("- **Rationale**: ").append(assessment.rationale()).append("\n");
			if (!assessment.findings().isEmpty()) {
				sb.append("- **Findings**:\n");
				assessment.findings().forEach(f -> sb.append("  - ").append(f).append("\n"));
			}
			sb.append("\n");
		}
	}

	private void appendFileTable(StringBuilder sb, List<FileChange> files) {
		sb.append("| File | Status | +/- |\n");
		sb.append("|------|--------|-----|\n");
		for (var file : files) {
			sb.append("| `").append(file.filename()).append("` | ").append(file.status()).append(" | +");
			sb.append(file.additions()).append(" / -").append(file.deletions()).append(" |\n");
		}
	}

	private static String statusEmoji(boolean success) {
		return success ? "PASS" : "FAIL";
	}

	private static String buildStatusEmoji(BuildResult build) {
		if (build.skipped()) {
			return "SKIPPED";
		}
		return build.success() ? "PASS" : "FAIL";
	}

	private static String rebaseSummary(RebaseResult rebase) {
		if (rebase.success()) {
			return "Clean rebase on " + rebase.branch();
		}
		return rebase.conflictFiles().size() + " conflicted files";
	}

	private static String buildSummary(BuildResult build) {
		if (build.skipped()) {
			return "Skipped (complex conflicts)";
		}
		return build.success() ? "All tests passed" : "Test failures detected";
	}

	private static String overallVerdict(ReviewReport report) {
		if (report.judgments().isEmpty()) {
			return "No judge verdicts available";
		}
		boolean allPass = report.judgments()
			.stream()
			.allMatch(j -> j.status() == org.springaicommunity.judge.result.JudgmentStatus.PASS);
		if (allPass) {
			return "PASS — All judges approved";
		}
		return "FAIL — One or more judges flagged issues";
	}

	private static String formatDuration(long durationMs) {
		Duration d = Duration.ofMillis(durationMs);
		if (d.toMinutes() > 0) {
			return d.toMinutes() + "m " + (d.toSeconds() % 60) + "s";
		}
		return d.toSeconds() + "s";
	}

}
