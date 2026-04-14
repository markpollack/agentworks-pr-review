package io.github.markpollack.prreview.steps;

import io.github.markpollack.prreview.github.GitHubRestClient;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.TestPrContexts;
import io.github.markpollack.workflow.core.AgentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FetchPrContextStepTest {

	@Mock
	private GitHubRestClient gitHubClient;

	@InjectMocks
	private FetchPrContextStep step;

	@Test
	void shouldReturnPrContextFromGitHubClient() {
		PrContext expected = TestPrContexts.pr5774();
		given(this.gitHubClient.fetchPrContext(5774)).willReturn(expected);

		PrContext result = this.step.execute(AgentContext.create(), 5774);

		assertThat(result).isEqualTo(expected);
		assertThat(result.number()).isEqualTo(5774);
	}

	@Test
	void shouldPublishPrContextToAgentContext() {
		PrContext pr = TestPrContexts.pr5774();
		given(this.gitHubClient.fetchPrContext(5774)).willReturn(pr);

		PrContext output = this.step.execute(AgentContext.create(), 5774);
		AgentContext enriched = this.step.updateContext(AgentContext.create(), output);

		assertThat(enriched.get(FetchPrContextStep.PR_CONTEXT)).isPresent().contains(pr);
	}

	@Test
	void shouldHaveCorrectName() {
		assertThat(this.step.name()).isEqualTo("fetch-pr-context");
	}

	@Test
	void shouldDeclareTypes() {
		assertThat(this.step.inputType()).isEqualTo(Integer.class);
		assertThat(this.step.outputType()).isEqualTo(PrContext.class);
	}

}
