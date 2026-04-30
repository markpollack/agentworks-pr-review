package io.github.markpollack.prreview.dsl;

import java.nio.file.Path;
import java.util.List;

import io.github.markpollack.journal.Journal;
import io.github.markpollack.journal.Run;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.core.AgentHandler;
import io.github.markpollack.workflow.core.Description;
import io.github.markpollack.workflow.flows.agent.Agent;
import io.github.markpollack.workflow.flows.workflow.Workflow;
import io.github.markpollack.workflow.flows.workflow.WorkflowExecutor;
import io.github.markpollack.workflow.journal.WorkflowJournal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DSL-based PR review pipeline — structurally equivalent to {@code PrReviewWorkflow} but
 * composed using the Workflow DSL for declarative readability.
 *
 * <p>
 * Three sub-workflows:
 * <ul>
 * <li>{@code aiAssessment}: ExtractPrContext → parallel(assessCodeQuality,
 * assessBackport)</li>
 * <li>{@code assessAndReport}: T1 → aiAssessment → T2 → report</li>
 * <li>{@code earlyReport}: T0 failure path → report</li>
 * </ul>
 *
 * <p>
 * The T0 gate is the structural pivot: onPass runs {@code assessAndReport}, onFail runs
 * {@code earlyReport}. Both produce {@code Path}.
 */
@Agent("pr-review-dsl")
@Description("DSL-based PR review pipeline")
public class PrReviewDslWorkflow implements AgentHandler<Integer, Path> {

	private static final Logger logger = LoggerFactory.getLogger(PrReviewDslWorkflow.class);

	private final Workflow<Integer, Path> pipeline;

	public PrReviewDslWorkflow(Workflow<Integer, Object> contextPhase, BuildGate buildGate,
			Workflow<Object, Path> assessAndReport, Workflow<Object, Path> earlyReport) {

		this.pipeline = Workflow.<Integer, Path>define("pr-review")
			.step(contextPhase)
			.gate(buildGate)
			.onPass(assessAndReport)
			.onFail(earlyReport)
			.end()
			.build();
	}

	@Override
	public Path handle(AgentContext ctx, Integer prNumber) {
		logger.info("=== PR Review Pipeline (DSL): PR #{} ===", prNumber);

		AgentContext seedCtx = ctx.mutate()
			.with(DslContextKeys.JUDGMENTS, List.of())
			.with(DslContextKeys.ASSESSMENTS, List.of())
			.with(DslContextKeys.OVERALL_VERDICT, "PASS")
			.build();

		try (Run run = Journal.run("pr-review").name("PR #" + prNumber).start()) {
			WorkflowExecutor executor = new WorkflowExecutor(WorkflowJournal.forRun(run));
			return executor.execute(this.pipeline.graph(), seedCtx, prNumber);
		}
	}

	public Workflow<Integer, Path> pipeline() {
		return this.pipeline;
	}

}
