package io.github.markpollack.prreview.steps;

import io.github.markpollack.prreview.github.GitHubRestClient;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.PrRequest;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.core.ContextKey;
import io.github.markpollack.workflow.core.Description;
import io.github.markpollack.workflow.core.StepName;
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
 *
 * <p>
 * <b>Two doors, one body.</b> The v3 authoring surface's leaf is
 * {@code Step<PrRequest,PrContext>} and holds the work; the v1 leaf the running pipeline
 * executes delegates to it. The input types differ because they say different things:
 * {@code Integer} is what a step is handed, {@link PrRequest} is what a <em>run</em> is
 * submitted with, and only the latter can name a field in {@code inputSchema}.
 */
@Component
@StepName("fetch-pr-context")
@Description("Fetches complete PR context from GitHub REST API")
public class FetchPrContextStep
		implements Step<Integer, PrContext>, io.github.markpollack.workflow.flows.v3.Step<PrRequest, PrContext> {

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
		return execute(new PrRequest(prNumber));
	}

	@Override
	public PrContext execute(PrRequest request) {
		logger.info("Fetching PR context for #{}", request.pr());
		PrContext prContext = this.gitHubClient.fetchPrContext(request.pr());
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
