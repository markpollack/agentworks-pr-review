package io.github.markpollack.prreview.config;

import java.util.List;
import java.util.Map;

import io.github.markpollack.prreview.PrReviewWorkflow;
import io.github.markpollack.workflow.core.AgentRegistry;
import io.github.markpollack.workflow.flows.agent.AgentExceptionHandlerResolver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the annotation model integration.
 */
@Configuration(proxyBeanMethods = false)
public class PrReviewConfig {

	@Bean
	AgentExceptionHandlerResolver agentExceptionHandlerResolver(PrReviewErrorAdvice errorAdvice) {
		return new AgentExceptionHandlerResolver(List.of(errorAdvice));
	}

	@Bean
	AgentRegistry agentRegistry(PrReviewWorkflow prReviewWorkflow) {
		return new AgentRegistry(Map.of("pr-review", prReviewWorkflow));
	}

}
