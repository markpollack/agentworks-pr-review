package com.tuvium.prreview.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainModelTest {

	@Nested
	class PrContextTests {

		@Test
		void shouldCreateWithAllFields() {
			var files = List.of(new FileChange("README.md", "modified", 10, 2, "@@ -1,3 +1,5 @@"));
			var comments = List.of(new Comment("alice", "LGTM", Instant.parse("2025-01-01T00:00:00Z")));
			var reviews = List.of(new Review("bob", "APPROVED", "Ship it", Instant.parse("2025-01-02T00:00:00Z")));
			var issues = List.of(new Issue(100, "Fix bug", List.of("bug")));

			var pr = new PrContext(5774, "Add feature", "Description", "author", List.of("enhancement"), "open", "main",
					"feature-branch", files, comments, reviews, issues);

			assertThat(pr.number()).isEqualTo(5774);
			assertThat(pr.title()).isEqualTo("Add feature");
			assertThat(pr.description()).isEqualTo("Description");
			assertThat(pr.labels()).containsExactly("enhancement");
			assertThat(pr.files()).hasSize(1);
			assertThat(pr.comments()).hasSize(1);
			assertThat(pr.reviews()).hasSize(1);
			assertThat(pr.linkedIssues()).hasSize(1);
		}

		@Test
		void shouldDefensivelyCopyLists() {
			var mutableLabels = new ArrayList<>(List.of("bug"));
			var pr = new PrContext(1, "Title", null, "author", mutableLabels, "open", "main", "fix", List.of(),
					List.of(), List.of(), List.of());

			mutableLabels.add("wontfix");
			assertThat(pr.labels()).containsExactly("bug");
		}

		@Test
		void shouldRejectNullListElements() {
			assertThatThrownBy(() -> new PrContext(1, "Title", null, "author", null, "open", "main", "fix", List.of(),
					List.of(), List.of(), List.of()))
				.isInstanceOf(NullPointerException.class);
		}

	}

	@Nested
	class RebaseResultTests {

		@Test
		void cleanFactoryMethod() {
			var result = RebaseResult.clean("feature-branch");
			assertThat(result.success()).isTrue();
			assertThat(result.branch()).isEqualTo("feature-branch");
			assertThat(result.conflictFiles()).isEmpty();
			assertThat(result.errorMessage()).isNull();
		}

		@Test
		void conflictFactoryMethod() {
			var result = RebaseResult.conflict("feature-branch", List.of("pom.xml", "README.md"));
			assertThat(result.success()).isFalse();
			assertThat(result.conflictFiles()).containsExactly("pom.xml", "README.md");
			assertThat(result.errorMessage()).contains("2 files");
		}

		@Test
		void shouldDefensivelyCopyConflictFiles() {
			var mutableFiles = new ArrayList<>(List.of("pom.xml"));
			var result = new RebaseResult(false, "branch", mutableFiles, "error");

			mutableFiles.add("build.gradle");
			assertThat(result.conflictFiles()).containsExactly("pom.xml");
		}

	}

	@Nested
	class ConflictReportTests {

		@Test
		void cleanFactoryMethod() {
			var report = ConflictReport.clean();
			assertThat(report.conflicts()).isEmpty();
			assertThat(report.hasComplexConflicts()).isFalse();
			assertThat(report.summary()).isEqualTo("Clean rebase, no conflicts");
		}

		@Test
		void shouldDefensivelyCopyConflicts() {
			var conflict = new ConflictFile("pom.xml", Classification.SIMPLE, "version bump");
			var mutableConflicts = new ArrayList<>(List.of(conflict));
			var report = new ConflictReport(mutableConflicts, false, "One simple conflict");

			mutableConflicts.clear();
			assertThat(report.conflicts()).hasSize(1);
		}

	}

	@Nested
	class BuildResultTests {

		@Test
		void skippedFactoryMethod() {
			var result = BuildResult.skippedBuild();
			assertThat(result.success()).isFalse();
			assertThat(result.skipped()).isTrue();
			assertThat(result.modules()).isEmpty();
			assertThat(result.output()).isNull();
			assertThat(result.durationMs()).isZero();
		}

		@Test
		void shouldDefensivelyCopyModules() {
			var mutableModules = new ArrayList<>(List.of("spring-ai-core"));
			var result = new BuildResult(true, false, mutableModules, "BUILD SUCCESS", 5000);

			mutableModules.add("spring-ai-openai");
			assertThat(result.modules()).containsExactly("spring-ai-core");
		}

	}

	@Nested
	class AssessmentResultTests {

		@Test
		void shouldCreateWithAllFields() {
			var result = new AssessmentResult("QualityJudge", JudgmentStatus.PASS, 0.85, "Good quality",
					List.of("Clean API", "Missing tests"));

			assertThat(result.judgeName()).isEqualTo("QualityJudge");
			assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(result.score()).isEqualTo(0.85);
			assertThat(result.rationale()).isEqualTo("Good quality");
			assertThat(result.findings()).containsExactly("Clean API", "Missing tests");
		}

		@Test
		void shouldDefensivelyCopyFindings() {
			var mutableFindings = new ArrayList<>(List.of("finding1"));
			var result = new AssessmentResult("Judge", JudgmentStatus.PASS, 1.0, "ok", mutableFindings);

			mutableFindings.add("finding2");
			assertThat(result.findings()).containsExactly("finding1");
		}

	}

	@Nested
	class ReviewReportTests {

		@Test
		void shouldCreateCompleteReport() {
			var prContext = new PrContext(5774, "Title", null, "author", List.of(), "open", "main", "branch", List.of(),
					List.of(), List.of(), List.of());
			var rebase = RebaseResult.clean("branch");
			var conflicts = ConflictReport.clean();
			var build = new BuildResult(true, false, List.of("core"), "SUCCESS", 3000);
			var assessment = new AssessmentResult("QualityJudge", JudgmentStatus.PASS, 0.9, "Good", List.of());
			var judgment = Judgment.pass("All checks passed");

			var report = new ReviewReport(prContext, rebase, conflicts, build, List.of(assessment), List.of(judgment),
					Instant.now());

			assertThat(report.prContext().number()).isEqualTo(5774);
			assertThat(report.assessments()).hasSize(1);
			assertThat(report.judgments()).hasSize(1);
			assertThat(report.generatedAt()).isNotNull();
		}

		@Test
		void shouldDefensivelyCopyAssessmentsAndJudgments() {
			var prContext = new PrContext(1, "T", null, "a", List.of(), "open", "main", "b", List.of(), List.of(),
					List.of(), List.of());
			var mutableAssessments = new ArrayList<>(
					List.of(new AssessmentResult("J", JudgmentStatus.PASS, 1.0, "ok", List.of())));
			var mutableJudgments = new ArrayList<>(List.of(Judgment.pass("ok")));

			var report = new ReviewReport(prContext, RebaseResult.clean("b"), ConflictReport.clean(),
					BuildResult.skippedBuild(), mutableAssessments, mutableJudgments, Instant.now());

			mutableAssessments.clear();
			mutableJudgments.clear();
			assertThat(report.assessments()).hasSize(1);
			assertThat(report.judgments()).hasSize(1);
		}

	}

	@Nested
	class ClassificationTests {

		@Test
		void shouldHaveTwoValues() {
			assertThat(Classification.values()).containsExactly(Classification.SIMPLE, Classification.COMPLEX);
		}

	}

	@Nested
	class IssueTests {

		@Test
		void shouldDefensivelyCopyLabels() {
			var mutableLabels = new ArrayList<>(List.of("bug"));
			var issue = new Issue(42, "Fix it", mutableLabels);

			mutableLabels.add("critical");
			assertThat(issue.labels()).containsExactly("bug");
		}

	}

	@Nested
	class RecordEqualityTests {

		@Test
		void fileChangesWithSameFieldsShouldBeEqual() {
			var a = new FileChange("f.java", "modified", 5, 3, "patch");
			var b = new FileChange("f.java", "modified", 5, 3, "patch");
			assertThat(a).isEqualTo(b);
			assertThat(a.hashCode()).isEqualTo(b.hashCode());
		}

		@Test
		void commentsWithSameFieldsShouldBeEqual() {
			var ts = Instant.parse("2025-01-01T00:00:00Z");
			var a = new Comment("alice", "LGTM", ts);
			var b = new Comment("alice", "LGTM", ts);
			assertThat(a).isEqualTo(b);
		}

	}

}
