package io.github.markpollack.prreview.dsl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.markpollack.prreview.config.WorkshopProperties;
import io.github.markpollack.prreview.github.GitHubRestClient;
import io.github.markpollack.prreview.judges.BuildJudge;
import io.github.markpollack.prreview.judges.QualityJudge;
import io.github.markpollack.prreview.judges.VersionPatternJudge;
import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.FileChange;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.prreview.model.TestPrContexts;
import io.github.markpollack.prreview.steps.AssessBackportStep;
import io.github.markpollack.prreview.steps.AssessCodeQualityStep;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.FixTestsStep;
import io.github.markpollack.prreview.steps.GenerateReportStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.workflow.Workflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agents.client.AgentClient;
import org.springaicommunity.agents.client.AgentClientResponse;
import org.springaicommunity.agents.model.AgentGeneration;
import org.springaicommunity.agents.model.AgentResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PrReviewDslWorkflowTest {

	private FetchPrContextStep fetchPrContext;

	private RebaseStep rebaseStep;

	private ConflictDetectionStep conflictDetection;

	private RunTestsStep runTests;

	private FixTestsStep fixTests;

	private AssessCodeQualityStep assessCodeQuality;

	private AssessBackportStep assessBackport;

	private GenerateReportStep generateReport;

	private AgentClient agentClient;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		GitHubRestClient gitHub = mock(GitHubRestClient.class);
		given(gitHub.fetchPrContext(anyInt())).willReturn(TestPrContexts.pr5774());

		this.fetchPrContext = new FetchPrContextStep(gitHub);
		this.rebaseStep = mock(RebaseStep.class);
		this.conflictDetection = new ConflictDetectionStep();
		this.runTests = mock(RunTestsStep.class);
		this.fixTests = mock(FixTestsStep.class);
		this.agentClient = mock(AgentClient.class);
		this.assessCodeQuality = new AssessCodeQualityStep(this.agentClient);
		this.assessBackport = new AssessBackportStep(this.agentClient);
		this.generateReport = new GenerateReportStep().outputDirectory(this.tempDir);

		// The DSL executor calls updateContext() on every step. Mocked steps must
		// propagate context with proper ContextKey writes, otherwise ctx becomes null.
		given(this.rebaseStep.updateContext(any(AgentContext.class), any(RebaseResult.class))).willAnswer(inv -> {
			AgentContext ctx = inv.getArgument(0);
			RebaseResult result = inv.getArgument(1);
			return ctx.mutate().with(RebaseStep.REBASE_RESULT, result).build();
		});
		given(this.runTests.updateContext(any(AgentContext.class), any(BuildResult.class))).willAnswer(inv -> {
			AgentContext ctx = inv.getArgument(0);
			BuildResult result = inv.getArgument(1);
			return ctx.mutate().with(RunTestsStep.BUILD_RESULT, result).build();
		});
	}

	@Test
	void fullPipeline_allGreen_generatesPassingReport() {
		given(this.rebaseStep.execute(any(AgentContext.class), any(PrContext.class)))
			.willReturn(RebaseResult.clean("fix/889-body-error-propagation"));
		given(this.runTests.execute(any(AgentContext.class), any(ConflictReport.class)))
			.willReturn(new BuildResult(true, false, List.of("mcp-spring-webflux"), "BUILD SUCCESS", 5000));
		given(this.agentClient.run(anyString())).willReturn(agentResponse("""
				{
				  "score": 0.85,
				  "status": "PASS",
				  "rationale": "Clean code with good test coverage",
				  "findings": ["Well-structured error handling"]
				}
				"""));

		PrReviewDslWorkflow workflow = createDslWorkflow();
		Path report = workflow.handle(AgentContext.create(), 5774);

		assertThat(report).exists();
		String content = readFile(report);
		assertThat(content).contains("PR Review Report: #5774").contains("PASS — All judges approved");
	}

	@Test
	void pipeline_buildFails_routesToEarlyReport() {
		given(this.rebaseStep.execute(any(AgentContext.class), any(PrContext.class)))
			.willReturn(RebaseResult.conflict("fix/branch", List.of("pom.xml", "src/main/java/Config.java")));
		given(this.runTests.execute(any(AgentContext.class), any(ConflictReport.class)))
			.willReturn(BuildResult.skippedBuild());

		PrReviewDslWorkflow workflow = createDslWorkflow();
		Path report = workflow.handle(AgentContext.create(), 5774);

		assertThat(report).exists();
		verify(this.agentClient, never()).run(anyString());
		String content = readFile(report);
		assertThat(content).contains("FAIL — One or more judges flagged issues");
	}

	@Test
	void pipeline_versionPatternFails_stillRunsAiAssessment() {
		PrContext prWithJavax = new PrContext(5774, "Add javax import", "This PR adds javax.servlet import", "author",
				List.of(), "open", "main", "fix/javax", List.of(new FileChange("src/main/java/App.java", "modified", 5,
						0, "+import javax.servlet.http.HttpServletRequest;")),
				List.of(), List.of(), List.of());

		GitHubRestClient gitHub = mock(GitHubRestClient.class);
		given(gitHub.fetchPrContext(anyInt())).willReturn(prWithJavax);
		FetchPrContextStep fetchStep = new FetchPrContextStep(gitHub);

		given(this.rebaseStep.execute(any(AgentContext.class), any(PrContext.class)))
			.willReturn(RebaseResult.clean("fix/javax"));
		given(this.runTests.execute(any(AgentContext.class), any(ConflictReport.class)))
			.willReturn(new BuildResult(true, false, List.of("module"), "BUILD SUCCESS", 3000));
		given(this.agentClient.run(anyString())).willReturn(agentResponse("""
				{
				  "score": 0.7,
				  "status": "PASS",
				  "rationale": "Advisory review despite deprecated APIs",
				  "findings": ["Uses javax imports"]
				}
				"""));

		PrReviewDslWorkflow workflow = createDslWorkflow(fetchStep);

		Path report = workflow.handle(AgentContext.create(), 5774);

		assertThat(report).exists();
		String content = readFile(report);
		assertThat(content).contains("FAIL");
	}

	@Test
	void fixAndRetest_attemptedWhenTestsFail() {
		WorkshopProperties props = new WorkshopProperties(5774, true, this.tempDir.toString(), ".", false);
		FixAndRetestStep step = new FixAndRetestStep(this.fixTests, this.runTests, props);

		RebaseResult rebase = RebaseResult.clean("fix/branch");
		ConflictReport conflicts = ConflictReport.clean();
		BuildResult failedBuild = new BuildResult(false, false, List.of("module"), "TEST FAILURE", 3000);
		BuildResult fixedBuild = new BuildResult(true, false, List.of("module"), "BUILD SUCCESS", 2000);

		given(this.fixTests.execute(any(AgentContext.class), any(BuildResult.class)))
			.willReturn(new io.github.markpollack.prreview.model.FixResult(true, true, List.of(), "Fixed"));
		given(this.runTests.execute(any(AgentContext.class), any(ConflictReport.class))).willReturn(fixedBuild);

		AgentContext ctx = AgentContext.create()
			.mutate()
			.with(RebaseStep.REBASE_RESULT, rebase)
			.with(ConflictDetectionStep.CONFLICT_REPORT, conflicts)
			.build();

		BuildResult result = step.execute(ctx, failedBuild);

		assertThat(result.success()).isTrue();
		verify(this.fixTests).execute(any(AgentContext.class), any(BuildResult.class));
	}

	@Test
	void fixAndRetest_skippedWhenTestsPass() {
		WorkshopProperties props = new WorkshopProperties(5774, true, this.tempDir.toString(), ".", false);
		FixAndRetestStep step = new FixAndRetestStep(this.fixTests, this.runTests, props);

		RebaseResult rebase = RebaseResult.clean("fix/branch");
		ConflictReport conflicts = ConflictReport.clean();
		BuildResult passingBuild = new BuildResult(true, false, List.of("module"), "BUILD SUCCESS", 3000);

		AgentContext ctx = AgentContext.create()
			.mutate()
			.with(RebaseStep.REBASE_RESULT, rebase)
			.with(ConflictDetectionStep.CONFLICT_REPORT, conflicts)
			.build();

		BuildResult result = step.execute(ctx, passingBuild);

		assertThat(result).isSameAs(passingBuild);
		verify(this.fixTests, never()).execute(any(AgentContext.class), any(BuildResult.class));
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private PrReviewDslWorkflow createDslWorkflow() {
		return createDslWorkflow(this.fetchPrContext);
	}

	private PrReviewDslWorkflow createDslWorkflow(FetchPrContextStep fetchStep) {
		WorkshopProperties props = new WorkshopProperties(5774, false, this.tempDir.toString(), ".", false);

		Workflow<Integer, Object> contextPhase = Workflow.<Integer, Object>define("context-phase")
			.step(fetchStep)
			.then(this.rebaseStep)
			.then(this.conflictDetection)
			.then(this.runTests)
			.then(new FixAndRetestStep(this.fixTests, this.runTests, props))
			.then(new CleanupStep(this.rebaseStep))
			.build();

		Workflow<Object, Object> aiAssessment = Workflow.<Object, Object>define("ai-assessment")
			.step(new ExtractPrContextStep())
			.parallel(this.assessCodeQuality, this.assessBackport)
			.build();

		Workflow<Object, Path> earlyReport = Workflow.<Object, Path>define("early-report")
			.step(new AssembleReportStep())
			.then(this.generateReport)
			.build();

		Workflow<Object, Path> assessAndReport = Workflow.<Object, Path>define("assess-and-report")
			.step(new VersionPatternStep(new VersionPatternJudge()))
			.then(aiAssessment)
			.then(new QualityJudgeStep(new QualityJudge(this.agentClient)))
			.then(new AssembleReportStep())
			.then(this.generateReport)
			.build();

		return new PrReviewDslWorkflow(contextPhase, new BuildGate(new BuildJudge()), assessAndReport, earlyReport);
	}

	private static AgentClientResponse agentResponse(String text) {
		AgentResponse response = new AgentResponse(List.of(new AgentGeneration(text)));
		return new AgentClientResponse(response);
	}

	private static String readFile(Path path) {
		try {
			return Files.readString(path);
		}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

}
