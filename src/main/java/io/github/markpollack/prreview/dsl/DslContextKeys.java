package io.github.markpollack.prreview.dsl;

import java.util.List;

import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.FixResult;
import io.github.markpollack.workflow.core.ContextKey;
import org.springaicommunity.judge.result.Judgment;

/**
 * Context keys specific to the DSL workflow pipeline. Keys for individual step outputs
 * live on the producing step class (e.g. {@code RebaseStep.REBASE_RESULT}).
 */
public final class DslContextKeys {

	@SuppressWarnings("unchecked")
	public static final ContextKey<List<Judgment>> JUDGMENTS = ContextKey.of("dsl.judgments",
			(Class<List<Judgment>>) (Class<?>) List.class);

	@SuppressWarnings("unchecked")
	public static final ContextKey<List<AssessmentResult>> ASSESSMENTS = ContextKey.of("dsl.assessments",
			(Class<List<AssessmentResult>>) (Class<?>) List.class);

	public static final ContextKey<String> OVERALL_VERDICT = ContextKey.of("dsl.overall-verdict", String.class);

	public static final ContextKey<FixResult> FIX_RESULT = ContextKey.of("dsl.fix-result", FixResult.class);

	private DslContextKeys() {
	}

}
