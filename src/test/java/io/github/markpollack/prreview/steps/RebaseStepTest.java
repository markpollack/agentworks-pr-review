package io.github.markpollack.prreview.steps;

import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.prreview.model.TestPrContexts;
import io.github.markpollack.workflow.flows.AgentContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RebaseStepTest {

	@Test
	void shouldHaveCorrectName() {
		assertThat(new RebaseStep().name()).isEqualTo("rebase-on-main");
	}

	@Test
	void shouldDeclareTypes() {
		RebaseStep step = new RebaseStep();
		assertThat(step.inputType()).isEqualTo(PrContext.class);
		assertThat(step.outputType()).isEqualTo(RebaseResult.class);
	}

	@Test
	void shouldReturnErrorResultOnException() {
		// Use a non-existent working directory to trigger an error
		RebaseStep step = new RebaseStep().workingDirectory(java.nio.file.Path.of("/nonexistent/repo"));
		PrContext input = TestPrContexts.pr5774();

		RebaseResult result = step.execute(AgentContext.create(), input);

		assertThat(result.success()).isFalse();
		assertThat(result.branch()).isEqualTo("fix/889-body-error-propagation");
		assertThat(result.errorMessage()).contains("Rebase error");
	}

}
