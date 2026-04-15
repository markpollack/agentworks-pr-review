package io.github.markpollack.prreview.dsl;

import io.github.markpollack.prreview.config.WorkshopProperties;
import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.FixResult;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FixTestsStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composite step that conditionally attempts to fix failing tests and re-runs them.
 *
 * <p>
 * Encapsulates the {@code shouldAttemptFix} decision logic by reading intermediate
 * results from context. If a fix is attempted, stores the {@link FixResult} in context.
 */
public class FixAndRetestStep implements Step<BuildResult, BuildResult> {

	private static final Logger logger = LoggerFactory.getLogger(FixAndRetestStep.class);

	private final FixTestsStep fixTestsStep;

	private final RunTestsStep runTestsStep;

	private final WorkshopProperties workshopProperties;

	private volatile FixResult lastFixResult;

	public FixAndRetestStep(FixTestsStep fixTestsStep, RunTestsStep runTestsStep,
			WorkshopProperties workshopProperties) {
		this.fixTestsStep = fixTestsStep;
		this.runTestsStep = runTestsStep;
		this.workshopProperties = workshopProperties;
	}

	@Override
	public String name() {
		return "fix-and-retest";
	}

	@Override
	public BuildResult execute(AgentContext ctx, BuildResult build) {
		RebaseResult rebase = ctx.get(RebaseStep.REBASE_RESULT).orElse(null);
		ConflictReport conflicts = ctx.get(ConflictDetectionStep.CONFLICT_REPORT).orElse(null);

		if (!shouldAttemptFix(rebase, conflicts, build)) {
			this.lastFixResult = null;
			return build;
		}

		logger.info("Attempting AI fix for test failures");
		FixResult fixResult = this.fixTestsStep.execute(ctx, build);
		this.lastFixResult = fixResult;
		logger.info("Fix result: attempted={}, fixed={}", fixResult.attempted(), fixResult.fixed());

		// Re-run tests after fix attempt
		logger.info("Re-running tests after AI fix");
		return this.runTestsStep.execute(ctx, conflicts);
	}

	@Override
	public AgentContext updateContext(AgentContext ctx, BuildResult output) {
		AgentContext.Builder builder = ctx.mutate().with(RunTestsStep.BUILD_RESULT, output);
		if (this.lastFixResult != null) {
			builder.with(DslContextKeys.FIX_RESULT, this.lastFixResult);
		}
		return builder.build();
	}

	private boolean shouldAttemptFix(RebaseResult rebase, ConflictReport conflicts, BuildResult build) {
		return this.workshopProperties.fixTests() && rebase != null && rebase.success() && conflicts != null
				&& !conflicts.hasComplexConflicts() && !build.skipped() && !build.success();
	}

	@Override
	public Class<?> inputType() {
		return BuildResult.class;
	}

	@Override
	public Class<?> outputType() {
		return BuildResult.class;
	}

}
