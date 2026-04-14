package io.github.markpollack.prreview.steps;

import io.github.markpollack.prreview.config.WorkshopProperties;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.prreview.model.TestPrContexts;
import io.github.markpollack.workflow.core.AgentContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RebaseStepTest {

	private static final WorkshopProperties TEST_PROPS = new WorkshopProperties(5774, false, false, ".", ".");

	@Test
	void shouldHaveCorrectName() {
		assertThat(new RebaseStep(TEST_PROPS).name()).isEqualTo("rebase-on-main");
	}

	@Test
	void shouldDeclareTypes() {
		RebaseStep step = new RebaseStep(TEST_PROPS);
		assertThat(step.inputType()).isEqualTo(PrContext.class);
		assertThat(step.outputType()).isEqualTo(RebaseResult.class);
	}

	@Test
	void shouldReturnErrorResultOnException() {
		// Use a non-existent working directory to trigger an error
		RebaseStep step = new RebaseStep(TEST_PROPS).workingDirectory(java.nio.file.Path.of("/nonexistent/repo"));
		PrContext input = TestPrContexts.pr5774();

		RebaseResult result = step.execute(AgentContext.create(), input);

		assertThat(result.success()).isFalse();
		assertThat(result.branch()).isEqualTo("review/pr-5774");
		assertThat(result.errorMessage()).contains("Rebase error");
	}

}
