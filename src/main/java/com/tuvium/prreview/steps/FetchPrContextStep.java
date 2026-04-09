package com.tuvium.prreview.steps;

import com.tuvium.prreview.github.GitHubRestClient;
import com.tuvium.prreview.model.PrContext;
import io.github.markpollack.workflow.flows.AgentContext;
import io.github.markpollack.workflow.flows.ContextKey;
import io.github.markpollack.workflow.flows.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * First step in the PR review pipeline — fetches the complete PR context from GitHub.
 *
 * <p>
 * Takes a PR number as input and returns the full {@link PrContext}. Also publishes the
 * context under {@link #PR_CONTEXT} so downstream steps and judges can access it
 * independently of the I→O chain.
 */
@Component
public class FetchPrContextStep implements Step<Integer, PrContext> {

	private static final Logger logger = LoggerFactory.getLogger(FetchPrContextStep.class);

	/**
	 * Context key for accessing PrContext from any downstream step or judge.
	 */
	public static final ContextKey<PrContext> PR_CONTEXT = ContextKey.of("prContext", PrContext.class);

	private final GitHubRestClient gitHubClient;

	public FetchPrContextStep(GitHubRestClient gitHubClient) {
		this.gitHubClient = gitHubClient;
	}

	@Override
	public String name() {
		return "fetch-pr-context";
	}

	@Override
	public PrContext execute(AgentContext ctx, Integer prNumber) {
		logger.info("Fetching PR context for #{}", prNumber);
		PrContext prContext = this.gitHubClient.fetchPrContext(prNumber);
		logger.info("PR #{}: '{}' by {} — {} files, {} comments, {} reviews", prContext.number(), prContext.title(),
				prContext.author(), prContext.files().size(), prContext.comments().size(), prContext.reviews().size());
		return prContext;
	}

	@Override
	public AgentContext updateContext(AgentContext ctx, PrContext output) {
		return ctx.mutate().with(PR_CONTEXT, output).build();
	}

	@Override
	public Class<?> inputType() {
		return Integer.class;
	}

	@Override
	public Class<?> outputType() {
		return PrContext.class;
	}

}
