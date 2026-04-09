package io.github.markpollack.prreview.steps;

import java.util.List;

import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.FileChange;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.TestPrContexts;
import io.github.markpollack.workflow.flows.AgentContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunTestsStepTest {

	private final RunTestsStep step = new RunTestsStep();

	@Test
	void shouldHaveCorrectName() {
		assertThat(this.step.name()).isEqualTo("run-tests");
	}

	@Nested
	class SkipOnComplexConflicts {

		@Test
		void shouldSkipWhenComplexConflictsDetected() {
			ConflictReport complex = new ConflictReport(
					List.of(new io.github.markpollack.prreview.model.ConflictFile("Config.java",
							io.github.markpollack.prreview.model.Classification.COMPLEX, "overlapping edits")),
					true, "1 complex conflict");

			AgentContext ctx = AgentContext.create()
				.mutate()
				.with(FetchPrContextStep.PR_CONTEXT, TestPrContexts.pr5774())
				.build();

			BuildResult result = step.execute(ctx, complex);

			assertThat(result.skipped()).isTrue();
			assertThat(result.success()).isFalse();
			assertThat(result.modules()).isEmpty();
		}

	}

	@Nested
	class ModuleDiscoveryTests {

		@Test
		void shouldDiscoverModulesFromFilePaths() {
			List<String> modules = ModuleDiscovery.discoverModules(List.of(
					new FileChange("models/spring-ai-ollama/src/main/java/Ollama.java", "modified", 10, 2, null),
					new FileChange("models/spring-ai-ollama/src/test/java/OllamaTest.java", "modified", 5, 1, null),
					new FileChange("spring-ai-core/src/main/java/Core.java", "modified", 3, 1, null)));

			assertThat(modules).containsExactly("models/spring-ai-ollama", "spring-ai-core");
		}

		@Test
		void shouldMapRootFilesToRootModule() {
			List<String> modules = ModuleDiscovery
				.discoverModules(List.of(new FileChange("pom.xml", "modified", 1, 1, null)));

			assertThat(modules).containsExactly(".");
		}

		@Test
		void shouldDeduplicateModules() {
			List<String> modules = ModuleDiscovery
				.discoverModules(List.of(new FileChange("core/src/main/java/A.java", "modified", 1, 0, null),
						new FileChange("core/src/test/java/ATest.java", "added", 10, 0, null)));

			assertThat(modules).containsExactly("core");
		}

	}

	@Nested
	class MavenCommandConstruction {

		@Test
		void shouldBuildFullTestCommand() {
			List<String> command = RunTestsStep.buildMavenCommand(List.of("."));
			assertThat(command).containsExactly("./mvnw", "test", "-B");
		}

		@Test
		void shouldBuildTargetedTestCommand() {
			List<String> command = RunTestsStep.buildMavenCommand(List.of("models/spring-ai-ollama", "spring-ai-core"));
			assertThat(command).containsExactly("./mvnw", "test", "-B", "-pl", "models/spring-ai-ollama,spring-ai-core",
					"-am");
		}

	}

}
