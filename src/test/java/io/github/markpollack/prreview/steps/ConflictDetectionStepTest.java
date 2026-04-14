package io.github.markpollack.prreview.steps;

import java.util.List;

import io.github.markpollack.prreview.model.Classification;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.workflow.core.AgentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictDetectionStepTest {

	private final ConflictDetectionStep step = new ConflictDetectionStep();

	@Test
	void shouldHaveCorrectName() {
		assertThat(this.step.name()).isEqualTo("detect-conflicts");
	}

	@Nested
	class CleanRebase {

		@Test
		void shouldReturnCleanReport() {
			RebaseResult clean = RebaseResult.clean("feature-branch");

			ConflictReport report = step.execute(AgentContext.create(), clean);

			assertThat(report.conflicts()).isEmpty();
			assertThat(report.hasComplexConflicts()).isFalse();
			assertThat(report.summary()).isEqualTo("Clean rebase, no conflicts");
		}

	}

	@Nested
	class ConflictClassification {

		@Test
		void shouldClassifyPomXmlAsSimple() {
			assertThat(ConflictDetectionStep.classify("pom.xml")).isEqualTo(Classification.SIMPLE);
			assertThat(ConflictDetectionStep.classify("spring-ai-core/pom.xml")).isEqualTo(Classification.SIMPLE);
		}

		@Test
		void shouldClassifyBuildGradleAsSimple() {
			assertThat(ConflictDetectionStep.classify("build.gradle")).isEqualTo(Classification.SIMPLE);
			assertThat(ConflictDetectionStep.classify("build.gradle.kts")).isEqualTo(Classification.SIMPLE);
		}

		@Test
		void shouldClassifyPropertiesAsSimple() {
			assertThat(ConflictDetectionStep.classify("gradle.properties")).isEqualTo(Classification.SIMPLE);
			assertThat(ConflictDetectionStep.classify("src/main/resources/application.properties"))
				.isEqualTo(Classification.SIMPLE);
		}

		@Test
		void shouldClassifyPackageInfoAsSimple() {
			assertThat(ConflictDetectionStep.classify("src/main/java/com/example/package-info.java"))
				.isEqualTo(Classification.SIMPLE);
		}

		@Test
		void shouldClassifyJavaSourceAsComplex() {
			assertThat(ConflictDetectionStep.classify("src/main/java/com/example/Service.java"))
				.isEqualTo(Classification.COMPLEX);
		}

		@Test
		void shouldClassifyTestSourceAsComplex() {
			assertThat(ConflictDetectionStep.classify("src/test/java/com/example/ServiceTest.java"))
				.isEqualTo(Classification.COMPLEX);
		}

	}

	@Nested
	class ConflictReportGeneration {

		@Test
		void shouldGenerateReportWithMixedConflicts() {
			RebaseResult conflicts = RebaseResult.conflict("branch",
					List.of("pom.xml", "src/main/java/Config.java", "build.gradle"));

			ConflictReport report = step.execute(AgentContext.create(), conflicts);

			assertThat(report.conflicts()).hasSize(3);
			assertThat(report.hasComplexConflicts()).isTrue();
			assertThat(report.summary()).contains("3 conflicts").contains("2 simple").contains("1 complex");
		}

		@Test
		void shouldGenerateReportWithOnlySimpleConflicts() {
			RebaseResult conflicts = RebaseResult.conflict("branch", List.of("pom.xml", "gradle.properties"));

			ConflictReport report = step.execute(AgentContext.create(), conflicts);

			assertThat(report.hasComplexConflicts()).isFalse();
			assertThat(report.summary()).contains("2 conflicts").contains("2 simple");
		}

		@Test
		void shouldHandleSingleConflictGrammar() {
			RebaseResult conflicts = RebaseResult.conflict("branch", List.of("pom.xml"));

			ConflictReport report = step.execute(AgentContext.create(), conflicts);

			assertThat(report.summary()).startsWith("1 conflict:");
		}

	}

}
