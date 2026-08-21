package io.github.markpollack.prreview.experiment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import io.github.markpollack.prreview.dsl.AssembleReportStep;
import io.github.markpollack.prreview.dsl.DslContextKeys;
import io.github.markpollack.prreview.judges.BuildJudge;
import io.github.markpollack.prreview.judges.QualityJudge;
import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.Finding;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.prreview.model.ReviewReport;
import io.github.markpollack.prreview.model.TestPrContexts;
import io.github.markpollack.prreview.steps.AssessCodeQualityStep;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.GenerateReportStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.Step;
import io.github.markpollack.workflow.flows.workflow.Gate;
import io.github.markpollack.workflow.flows.workflow.GateAssessment;
import io.github.markpollack.workflow.flows.workflow.GateDecision;
import io.github.markpollack.workflow.flows.workflow.Workflow;
import io.github.markpollack.workflow.flows.workflow.WorkflowNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.markpollack.judge.jury.Verdict;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CompositeJuryContractTest {

	private static final Instant FIXED_TIME = Instant.parse("2026-08-12T12:00:00Z");

	@Test
	void realExperimentGatesRetainOrderedLeafJudgmentsAndReportBytes(@TempDir Path tempDir) throws Exception {
		PrReviewExperimentWorkflow workflow = experimentWorkflow();
		List<WorkflowNode.GateNode> gates = gateNodes(workflow.pipeline());
		assertThat(gates).hasSize(3);

		AssessmentResult failedQuality = new AssessmentResult("KbConsultingAssessStep", JudgmentStatus.ERROR, 0.0,
				"Assessment unavailable",
				List.of(new Finding(null, null, null, 0, "LLM did not return a review", null, null)));
		RebaseResult rebase = RebaseResult.clean("fix/889-body-error-propagation");
		ConflictReport conflicts = ConflictReport.clean();
		BuildResult build = new BuildResult(true, false, List.of("mcp-spring-webflux"), "BUILD SUCCESS", 5_000);
		AgentContext gateContext = AgentContext.create()
			.mutate()
			.with(RebaseStep.REBASE_RESULT, rebase)
			.with(ConflictDetectionStep.CONFLICT_REPORT, conflicts)
			.with(RunTestsStep.BUILD_RESULT, build)
			.with(AssessCodeQualityStep.QUALITY_ASSESSMENT, failedQuality)
			.build();

		GateAssessment.Decided buildAssessment = decided(evaluate(gates.get(0), gateContext));
		GateAssessment.Decided qualityPassArmAssessment = decided(evaluate(gates.get(1), gateContext));
		GateAssessment.Decided qualityFailArmAssessment = decided(evaluate(gates.get(2), gateContext));
		assertThat(buildAssessment.decision()).isEqualTo(GateDecision.PASS);
		assertThat(qualityPassArmAssessment.decision()).isEqualTo(GateDecision.FAIL);
		assertThat(qualityFailArmAssessment.decision()).isEqualTo(GateDecision.FAIL);

		Verdict buildVerdict = requireVerdict(buildAssessment);
		Verdict qualityVerdict = requireVerdict(qualityPassArmAssessment);
		Judgment buildLeaf = soleLeaf(buildVerdict);
		Judgment qualityLeaf = soleLeaf(qualityVerdict);

		AgentContext reportContext = gateContext.mutate()
			.with(FetchPrContextStep.PR_CONTEXT, TestPrContexts.pr5774())
			.with(DslContextKeys.JUDGMENTS, List.of())
			.with(DslContextKeys.ASSESSMENTS, List.of())
			.with(AgentContext.JUDGE_VERDICTS, List.<Object>of(buildVerdict, qualityVerdict))
			.build();
		ReviewReport assembled = new AssembleReportStep().execute(reportContext, new Object());
		ReviewReport postTraversal = atFixedTime(assembled);

		assertThat(postTraversal.judgments()).containsExactly(buildLeaf, qualityLeaf);
		assertThat(postTraversal.judgments().get(0)).isSameAs(buildLeaf);
		assertThat(postTraversal.judgments().get(1)).isSameAs(qualityLeaf);
		assertThat(buildLeaf.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(qualityLeaf.status()).isEqualTo(JudgmentStatus.FAIL);

		ReviewReport preTraversal = new ReviewReport(assembled.prContext(), assembled.rebaseResult(),
				assembled.conflictReport(), assembled.buildResult(), assembled.fixResult(), assembled.assessments(),
				List.of(buildLeaf, qualityLeaf), FIXED_TIME);
		byte[] preBytes = render(preTraversal, tempDir.resolve("pre-commit-b"));
		byte[] postBytes = render(postTraversal, tempDir.resolve("post-commit-b"));
		String preHash = sha256(preBytes);
		String postHash = sha256(postBytes);

		assertThat(postBytes).isEqualTo(preBytes);
		assertThat(postHash).isEqualTo(preHash);
		System.out.println("STEP_2_4D_BUILD_GATE=" + buildAssessment.decision());
		System.out.println("STEP_2_4D_QUALITY_GATE=" + qualityPassArmAssessment.decision());
		System.out.println("STEP_2_4D_PRE_B_REPORT_SHA256=" + preHash);
		System.out.println("STEP_2_4D_POST_B_REPORT_SHA256=" + postHash);
	}

	private static PrReviewExperimentWorkflow experimentWorkflow() {
		FetchPrContextStep fetch = namedMock(FetchPrContextStep.class, "fetch-pr-context");
		RebaseStep rebase = namedMock(RebaseStep.class, "rebase-on-main");
		ConflictDetectionStep conflicts = namedMock(ConflictDetectionStep.class, "detect-conflicts");
		RunTestsStep tests = namedMock(RunTestsStep.class, "run-tests");
		@SuppressWarnings("unchecked")
		Step<io.github.markpollack.prreview.model.PrContext, AssessmentResult> assess = mock(Step.class);
		given(assess.name()).willReturn("kb-consulting-assess");
		return new PrReviewExperimentWorkflow(fetch, rebase, conflicts, tests, assess, new BuildJudge(),
				new QualityJudge(null, false), new GenerateReportStep());
	}

	private static <T extends Step<?, ?>> T namedMock(Class<T> type, String name) {
		T step = mock(type);
		given(step.name()).willReturn(name);
		return step;
	}

	private static List<WorkflowNode.GateNode> gateNodes(Workflow<?, ?> workflow) {
		List<WorkflowNode.GateNode> gates = new ArrayList<>();
		for (WorkflowNode node : workflow.graph().nodes()) {
			if (node instanceof WorkflowNode.GateNode gate) {
				gates.add(gate);
			}
			else if (node instanceof WorkflowNode.StepNode step && step.step() instanceof Workflow<?, ?> nested) {
				gates.addAll(gateNodes(nested));
			}
		}
		return gates;
	}

	@SuppressWarnings("unchecked")
	private static GateAssessment evaluate(WorkflowNode.GateNode node, AgentContext context) {
		return ((Gate<Object>) node.gate()).evaluate(context, new Object());
	}

	private static GateAssessment.Decided decided(GateAssessment assessment) {
		assertThat(assessment).isInstanceOf(GateAssessment.Decided.class);
		return (GateAssessment.Decided) assessment;
	}

	private static Verdict requireVerdict(GateAssessment.Decided assessment) {
		assertThat(assessment.verdict()).isNotNull();
		return assessment.verdict();
	}

	private static Judgment soleLeaf(Verdict verdict) {
		assertThat(verdict.compositeAttempts()).hasSize(1);
		Verdict tier = verdict.compositeAttempts().get(0).verdict();
		assertThat(tier).isNotNull();
		assertThat(tier.compositeAttempts()).isEmpty();
		assertThat(tier.individual()).hasSize(1);
		return tier.individual().get(0);
	}

	private static ReviewReport atFixedTime(ReviewReport report) {
		return new ReviewReport(report.prContext(), report.rebaseResult(), report.conflictReport(),
				report.buildResult(), report.fixResult(), report.assessments(), report.judgments(), FIXED_TIME);
	}

	private static byte[] render(ReviewReport report, Path directory) throws Exception {
		Path reportPath = new GenerateReportStep().outputDirectory(directory).execute(AgentContext.create(), report);
		return Files.readAllBytes(reportPath);
	}

	private static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

}
