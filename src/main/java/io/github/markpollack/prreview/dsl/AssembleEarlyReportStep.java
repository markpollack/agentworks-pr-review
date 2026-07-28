package io.github.markpollack.prreview.dsl;

import java.time.Instant;
import java.util.List;

import io.github.markpollack.prreview.model.EarlyReportRequest;
import io.github.markpollack.prreview.model.ReviewReport;
import io.github.markpollack.workflow.flows.v3.Step;

/**
 * Assembles a {@link ReviewReport} on the arm where the build-health gate failed — the
 * half of {@link AssembleReportStep} that used to be reached by the same bean answering
 * {@code orElseGet(...)} on every value the failing path never produced.
 *
 * <p>
 * There is no v1 leaf here. The v1 pipeline has one report bean because it has no way to
 * say that two arms dispatch different values; this class exists because the v3 graph
 * does, and it is the one leaf in the pipeline the v1 executor never sees.
 */
public class AssembleEarlyReportStep implements Step<EarlyReportRequest, ReviewReport> {

	@Override
	public String name() {
		return "assemble-early-report";
	}

	@Override
	public ReviewReport execute(EarlyReportRequest request) {
		return new ReviewReport(request.context(), request.rebase(), request.conflicts(), request.build(), null,
				List.of(), List.of(), Instant.now());
	}

}
