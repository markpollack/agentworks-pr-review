package io.github.markpollack.prreview.v3;

import io.github.markpollack.prreview.model.ReviewReport;
import io.github.markpollack.prreview.steps.GenerateReportStep;
import io.github.markpollack.workflow.flows.v3.Step;

/** Portable v3 boundary for the legacy report writer's JVM-specific {@code Path}. */
final class GenerateReportV3Step implements Step<ReviewReport, String> {

	private final GenerateReportStep delegate;

	GenerateReportV3Step(GenerateReportStep delegate) {
		this.delegate = delegate;
	}

	@Override
	public String name() {
		return "generate-report";
	}

	@Override
	public String execute(ReviewReport report) {
		return this.delegate.execute(report).toString();
	}

}
