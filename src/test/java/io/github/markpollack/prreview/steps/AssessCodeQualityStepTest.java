package io.github.markpollack.prreview.steps;

import java.util.List;

import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.TestPrContexts;
import io.github.markpollack.workflow.core.AgentContext;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agents.client.AgentClient;
import org.springaicommunity.agents.client.AgentClientResponse;
import org.springaicommunity.agents.model.AgentGeneration;
import org.springaicommunity.agents.model.AgentResponse;
import org.springaicommunity.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AssessCodeQualityStepTest {

	private final AgentClient agentClient = mock(AgentClient.class);

	private final AssessCodeQualityStep step = new AssessCodeQualityStep(agentClient);

	@Test
	void shouldHaveCorrectName() {
		assertThat(step.name()).isEqualTo("assess-code-quality");
	}

	@Test
	void shouldParseSuccessfulAssessment() {
		String aiResponse = """
				{
				  "score": 0.9,
				  "status": "PASS",
				  "rationale": "Well-structured error propagation fix",
				  "findings": ["Good test coverage for the new behavior"]
				}
				""";
		given(agentClient.run(anyString())).willReturn(agentResponse(aiResponse));

		AssessmentResult result = step.execute(AgentContext.create(), TestPrContexts.pr5774());

		assertThat(result.judgeName()).isEqualTo("code-quality");
		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.score()).isEqualTo(0.9);
	}

	@Test
	void shouldReturnErrorOnAgentClientFailure() {
		given(agentClient.run(anyString())).willThrow(new RuntimeException("Claude unavailable"));

		AssessmentResult result = step.execute(AgentContext.create(), TestPrContexts.pr5774());

		assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(result.rationale()).contains("Claude unavailable");
	}

	@Test
	void shouldPublishToContext() {
		String aiResponse = """
				{ "score": 0.8, "status": "PASS", "rationale": "Good", "findings": [] }
				""";
		given(agentClient.run(anyString())).willReturn(agentResponse(aiResponse));

		AssessmentResult output = step.execute(AgentContext.create(), TestPrContexts.pr5774());
		AgentContext updated = step.updateContext(AgentContext.create(), output);

		assertThat(updated.require(AssessCodeQualityStep.QUALITY_ASSESSMENT)).isEqualTo(output);
	}

	@Test
	void shouldRenderPromptWithPrContext() {
		String prompt = AssessCodeQualityStep.renderPrompt(TestPrContexts.pr5774());

		assertThat(prompt).contains("PR #5774");
		assertThat(prompt).contains("Propagate body-level errors");
		assertThat(prompt).contains("Planview-JamesK");
	}

	private static AgentClientResponse agentResponse(String text) {
		AgentResponse response = new AgentResponse(List.of(new AgentGeneration(text)));
		return new AgentClientResponse(response);
	}

}
