package io.github.markpollack.prreview.steps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.markpollack.prreview.model.ReviewReport;
import io.github.markpollack.prreview.model.TestAssessments;
import io.github.markpollack.workflow.core.AgentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateReportStepTest {

	private final GenerateReportStep step = new GenerateReportStep();

	// ── Markdown tests ──────────────────────────────────────────────────

	@Test
	void renderMarkdown_passingReport_containsAllSections() {
		ReviewReport report = TestAssessments.passingReport();
		String markdown = this.step.renderMarkdown(report);

		assertThat(markdown).contains("# PR Review Report: #5774")
			.contains("Propagate body-level errors")
			.contains("Planview-JamesK")
			.contains("Phase 1: Deterministic Context Gathering")
			.contains("Phase 2: Judge Cascade")
			.contains("Phase 3: AI Assessments")
			.contains("Files Changed")
			.contains("WebClientStreamableHttpTransport.java");
	}

	@Test
	void renderMarkdown_passingReport_showsPassVerdict() {
		ReviewReport report = TestAssessments.passingReport();
		String markdown = this.step.renderMarkdown(report);

		assertThat(markdown).contains("PASS — All judges approved");
	}

	@Test
	void renderMarkdown_failedBuildReport_showsFailVerdict() {
		ReviewReport report = TestAssessments.failedBuildReport();
		String markdown = this.step.renderMarkdown(report);

		assertThat(markdown).contains("FAIL — One or more judges flagged issues");
	}

	@Test
	void renderMarkdown_failedBuildReport_noAiAssessments() {
		ReviewReport report = TestAssessments.failedBuildReport();
		String markdown = this.step.renderMarkdown(report);

		assertThat(markdown).contains("AI assessments were not run");
	}

	@Test
	void renderMarkdown_passingReport_fileTableHasCorrectColumns() {
		ReviewReport report = TestAssessments.passingReport();
		String markdown = this.step.renderMarkdown(report);

		assertThat(markdown).contains("| File | Status | +/- |").contains("modified").contains("added");
	}

	@Test
	void renderMarkdown_passingReport_showsLineCounts() {
		ReviewReport report = TestAssessments.passingReport();
		String markdown = this.step.renderMarkdown(report);

		// PR 5774: 17 additions + 230 additions = 247, 11 deletions
		assertThat(markdown).contains("+247 / -11");
	}

	@Test
	void execute_writesFileToOutputDirectory(@TempDir Path tempDir) throws IOException {
		this.step.outputDirectory(tempDir);
		ReviewReport report = TestAssessments.passingReport();

		Path result = this.step.execute(AgentContext.create(), report);

		assertThat(result).exists();
		assertThat(result.getFileName().toString()).isEqualTo("review-pr-5774.md");
		String content = Files.readString(result);
		assertThat(content).contains("# PR Review Report: #5774");
	}

	@Test
	void stepMetadata() {
		assertThat(this.step.name()).isEqualTo("generate-report");
		assertThat(this.step.inputType()).isEqualTo(ReviewReport.class);
		assertThat(this.step.outputType()).isEqualTo(Path.class);
	}

	// ── HTML tests ──────────────────────────────────────────────────────

	@Test
	void renderHtml_passingReport_containsHtmlStructure() {
		ReviewReport report = TestAssessments.passingReport();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("<!DOCTYPE html>")
			.contains("<html lang=\"en\">")
			.contains("</html>")
			.contains("<style>");
	}

	@Test
	void renderHtml_passingReport_containsPrMetadata() {
		ReviewReport report = TestAssessments.passingReport();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("PR #5774")
			.contains("Propagate body-level errors")
			.contains("Planview-JamesK")
			.contains("fix/889-body-error-propagation");
	}

	@Test
	void renderHtml_passingReport_containsJudgeCascade() {
		ReviewReport report = TestAssessments.passingReport();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("Judge Cascade")
			.contains("Build Judge")
			.contains("Version Pattern Judge")
			.contains("Quality Judge")
			.contains("T0")
			.contains("T1")
			.contains("T2");
	}

	@Test
	void renderHtml_passingReport_showsPassStatus() {
		ReviewReport report = TestAssessments.passingReport();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("status-pass").contains("PASS");
	}

	@Test
	void renderHtml_failedBuildReport_showsFailStatus() {
		ReviewReport report = TestAssessments.failedBuildReport();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("status-fail").contains("FAIL");
	}

	@Test
	void renderHtml_passingReport_containsGroupedCodeChanges() {
		ReviewReport report = TestAssessments.passingReport();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("Code Changes")
			.contains("file-group")
			.contains("Source")
			.contains("Test")
			.contains("WebClientStreamableHttpTransport.java")
			.contains("change-bar");
	}

	@Test
	void renderHtml_passingReport_containsExecutiveSummary() {
		ReviewReport report = TestAssessments.passingReport();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("Executive Summary")
			.contains("summary-grid")
			.contains("Problem")
			.contains("Quality Assessment")
			.contains("Backport Assessment")
			.contains("Clean code with good test coverage");
	}

	@Test
	void renderHtml_passingReport_containsTestResults() {
		ReviewReport report = TestAssessments.passingReport();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("Test Results")
			.contains("test-metrics")
			.contains("tm-value")
			.contains("mcp-spring-webflux")
			.contains("Build output");
	}

	@Test
	void renderHtml_passingReport_containsAdditionalInfo() {
		ReviewReport report = TestAssessments.passingReport();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("Additional Information")
			.contains("info-grid")
			.contains("PR Metadata")
			.contains("Assessment Summary")
			.contains("QualityJudge");
	}

	@Test
	void renderHtml_passingReport_containsDetailedAnalysis() {
		ReviewReport report = TestAssessments.passingReport();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("Detailed Analysis")
			.contains("analysis-details")
			.contains("Rebase")
			.contains("Conflict Detection")
			.contains("Build")
			.contains("Quality Findings");
	}

	@Test
	void renderHtml_passingReport_noDiscussionWhenEmpty() {
		ReviewReport report = TestAssessments.passingReport();
		String html = this.step.renderHtml(report);

		// PR 5774 has no reviews/comments so Discussion section should be absent
		assertThat(html).doesNotContain("<h2>Discussion</h2>");
	}

	@Test
	void renderHtml_reportWithReviews_containsDiscussion() {
		ReviewReport report = TestAssessments.passingReportWithReviews();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("Discussion")
			.contains("maintainer-bob")
			.contains("contributor-alice")
			.contains("CHANGES_REQUESTED")
			.contains("APPROVED")
			.contains("review-approved")
			.contains("review-changes-requested");
	}

	@Test
	void renderHtml_reportWithReviews_containsIssueBadges() {
		ReviewReport report = TestAssessments.passingReportWithReviews();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("badge-issue").contains("#1100");
	}

	@Test
	void renderHtml_passingReport_containsDescriptionInBanner() {
		ReviewReport report = TestAssessments.passingReport();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("pr-description").contains("onErrorComplete operator silently swallows");
	}

	@Test
	void renderHtml_failedBuildReport_showsTestsNotRun() {
		ReviewReport report = TestAssessments.failedBuildReport();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("Test Results");
	}

	@Test
	void renderHtml_passingReport_containsFooter() {
		ReviewReport report = TestAssessments.passingReport();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("AgentWorks PR Review Pipeline").contains("2026");
	}

	@Test
	void renderHtml_escapesSpecialCharacters() {
		String escaped = HtmlReportRenderer.escapeHtml("<script>alert('xss')</script>");

		assertThat(escaped).doesNotContain("<script>").contains("&lt;script&gt;").contains("&#39;");
	}

	@Test
	void execute_writesBothMdAndHtml(@TempDir Path tempDir) throws IOException {
		this.step.outputDirectory(tempDir);
		ReviewReport report = TestAssessments.passingReport();

		Path mdPath = this.step.execute(AgentContext.create(), report);

		assertThat(mdPath).exists();
		Path htmlPath = tempDir.resolve("review-pr-5774.html");
		assertThat(htmlPath).exists();

		String htmlContent = Files.readString(htmlPath);
		assertThat(htmlContent).contains("<!DOCTYPE html>").contains("PR #5774");
	}

	@Test
	void renderHtml_passingReport_checksExpandedByDefault() {
		ReviewReport report = TestAssessments.passingReport();
		String html = this.step.renderHtml(report);

		assertThat(html).contains("<details open>");
	}

}
