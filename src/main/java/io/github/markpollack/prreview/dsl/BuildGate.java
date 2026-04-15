package io.github.markpollack.prreview.dsl;

import java.time.Duration;
import java.time.Instant;

import io.github.markpollack.prreview.judges.BuildJudge;
import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.workflow.Gate;
import io.github.markpollack.workflow.flows.workflow.GateDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;

/**
 * T0 gate: evaluates build health using {@link BuildJudge}.
 *
 * <p>
 * Reads intermediate results from context and delegates to the deterministic judge.
 * Stores the judgment in a volatile field for retrieval by the companion
 * {@code recordT0Judgment} step that runs immediately after the gate.
 */
public class BuildGate implements Gate<Object> {

	private static final Logger logger = LoggerFactory.getLogger(BuildGate.class);

	private final BuildJudge buildJudge;

	// Temporal coupling: the companion recordT0Judgment step must read this
	// before any other gate evaluation overwrites it. Safe in the single-threaded
	// WorkflowExecutor.
	private volatile Judgment lastJudgment;

	public BuildGate(BuildJudge buildJudge) {
		this.buildJudge = buildJudge;
	}

	public Judgment lastJudgment() {
		return this.lastJudgment;
	}

	@Override
	public GateDecision evaluate(AgentContext ctx, Object output) {
		RebaseResult rebase = ctx.get(RebaseStep.REBASE_RESULT).orElse(null);
		ConflictReport conflicts = ctx.get(ConflictDetectionStep.CONFLICT_REPORT).orElse(null);
		BuildResult build = ctx.get(RunTestsStep.BUILD_RESULT).orElse(null);

		JudgmentContext.Builder builder = JudgmentContext.builder()
			.goal("Build health evaluation")
			.agentOutput("Build evaluation for PR")
			.executionTime(Duration.ZERO)
			.startedAt(Instant.now());
		putIfNotNull(builder, BuildJudge.REBASE_RESULT, rebase);
		putIfNotNull(builder, BuildJudge.CONFLICT_REPORT, conflicts);
		putIfNotNull(builder, BuildJudge.BUILD_RESULT, build);

		Judgment judgment = this.buildJudge.judge(builder.build());
		this.lastJudgment = withMeta(judgment);

		logger.info("T0 verdict: {} — {}", judgment.status(), judgment.reasoning());

		return judgment.status() == JudgmentStatus.PASS ? GateDecision.PASS : GateDecision.FAIL;
	}

	private static Judgment withMeta(Judgment judgment) {
		return Judgment.builder()
			.score(judgment.score())
			.status(judgment.status())
			.reasoning(judgment.reasoning())
			.checks(judgment.checks())
			.metadata("judge_name", "Build Judge")
			.metadata("tier", "T0")
			.build();
	}

	private static void putIfNotNull(JudgmentContext.Builder builder, String key, Object value) {
		if (value != null) {
			builder.metadata(key, value);
		}
	}

}
