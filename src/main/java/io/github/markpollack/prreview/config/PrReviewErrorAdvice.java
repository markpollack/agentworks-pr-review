package io.github.markpollack.prreview.config;

import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.core.ExceptionHandler;
import io.github.markpollack.workflow.flows.agent.AgentAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cross-cutting fallback error handler for all agents in the PR review pipeline.
 *
 * <p>
 * Catches any unhandled {@link Exception} that escapes an agent's own
 * {@link ExceptionHandler} methods. Logs the error and returns a descriptive message
 * rather than propagating the exception.
 */
@AgentAdvice
public class PrReviewErrorAdvice {

	private static final Logger logger = LoggerFactory.getLogger(PrReviewErrorAdvice.class);

	@ExceptionHandler(Exception.class)
	String handleAnyException(Exception ex, AgentContext ctx) {
		logger.error("Cross-cutting error handler caught: {}", ex.getMessage(), ex);
		return "Pipeline error: " + ex.getMessage();
	}

}
