package io.github.markpollack.prreview.steps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.markpollack.prreview.model.ReviewReport;
import io.github.markpollack.prreview.model.TestAssessments;
import io.github.markpollack.workflow.flows.AgentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateReportStepTest {

	private final GenerateReportStep step = new GenerateReportStep();

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

}
