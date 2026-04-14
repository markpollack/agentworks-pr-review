package io.github.markpollack.prreview.steps;

import java.util.List;

import io.github.markpollack.prreview.model.AssessmentResult;
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

class AssessBackportStepTest {

	private final AgentClient agentClient = mock(AgentClient.class);

	private final AssessBackportStep step = new AssessBackportStep(agentClient);

	@Test
	void shouldHaveCorrectName() {
		assertThat(step.name()).isEqualTo("assess-backport");
	}

	@Test
	void shouldParseSuccessfulAssessment() {
		String aiResponse = """
				{
				  "score": 0.8,
				  "status": "PASS",
				  "rationale": "Small focused fix, good backport candidate",
				  "findings": ["No API changes", "Minimal risk"]
				}
				""";
		given(agentClient.run(anyString())).willReturn(agentResponse(aiResponse));

		AssessmentResult result = step.execute(AgentContext.create(), TestPrContexts.pr5774());

		assertThat(result.judgeName()).isEqualTo("backport");
		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.score()).isEqualTo(0.8);
	}

	@Test
	void shouldReturnErrorOnAgentClientFailure() {
		given(agentClient.run(anyString())).willThrow(new RuntimeException("timeout"));

		AssessmentResult result = step.execute(AgentContext.create(), TestPrContexts.pr5774());

		assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
	}

	@Test
	void shouldPublishToContext() {
		String aiResponse = """
				{ "score": 0.7, "status": "PASS", "rationale": "OK", "findings": [] }
				""";
		given(agentClient.run(anyString())).willReturn(agentResponse(aiResponse));

		AssessmentResult output = step.execute(AgentContext.create(), TestPrContexts.pr5774());
		AgentContext updated = step.updateContext(AgentContext.create(), output);

		assertThat(updated.require(AssessBackportStep.BACKPORT_ASSESSMENT)).isEqualTo(output);
	}

	@Test
	void shouldRenderPromptWithPrContext() {
		String prompt = AssessBackportStep.renderPrompt(TestPrContexts.prWithReviews());

		assertThat(prompt).contains("PR #1175");
		assertThat(prompt).contains("type: enhancement");
	}

	private static AgentClientResponse agentResponse(String text) {
		AgentResponse response = new AgentResponse(List.of(new AgentGeneration(text)));
		return new AgentClientResponse(response);
	}

}
