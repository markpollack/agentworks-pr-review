package io.github.markpollack.prreview;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.markpollack.prreview.config.WorkshopProperties;
import io.github.markpollack.prreview.github.GitHubRestClient;
import io.github.markpollack.prreview.judges.BuildJudge;
import io.github.markpollack.prreview.judges.QualityJudge;
import io.github.markpollack.prreview.judges.VersionPatternJudge;
import io.github.markpollack.prreview.model.AssessmentResult;
import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.prreview.model.TestPrContexts;
import io.github.markpollack.prreview.steps.AssessBackportStep;
import io.github.markpollack.prreview.steps.AssessCodeQualityStep;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.GenerateReportStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.workflow.flows.AgentContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agents.client.AgentClient;
import org.springaicommunity.agents.client.AgentClientResponse;
import org.springaicommunity.agents.model.AgentGeneration;
import org.springaicommunity.agents.model.AgentResponse;
import org.springaicommunity.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PrReviewWorkflowTest {

	private FetchPrContextStep fetchPrContext;

	private RebaseStep rebaseStep;

	private ConflictDetectionStep conflictDetection;

	private RunTestsStep runTests;

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
		this.agentClient = mock(AgentClient.class);
		this.assessCodeQuality = new AssessCodeQualityStep(this.agentClient);
		this.assessBackport = new AssessBackportStep(this.agentClient);
		this.generateReport = new GenerateReportStep().outputDirectory(this.tempDir);
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

		PrReviewWorkflow workflow = createWorkflow(false);
		Path report = workflow.execute(5774);

		assertThat(report).exists();
		String content = readFile(report);
		assertThat(content).contains("PR Review Report: #5774").contains("PASS — All judges approved");
	}

	@Test
	void pipeline_buildFails_skipsAiAssessment() {
		given(this.rebaseStep.execute(any(AgentContext.class), any(PrContext.class)))
			.willReturn(RebaseResult.conflict("fix/branch", List.of("pom.xml", "src/main/java/Config.java")));
		given(this.runTests.execute(any(AgentContext.class), any(ConflictReport.class)))
			.willReturn(BuildResult.skippedBuild());

		PrReviewWorkflow workflow = createWorkflow(false);
		Path report = workflow.execute(5774);

		assertThat(report).exists();
		verify(this.agentClient, never()).run(anyString());
		String content = readFile(report);
		assertThat(content).contains("FAIL — One or more judges flagged issues");
	}

	@Test
	void pipeline_skipAi_skipsAiSteps() {
		given(this.rebaseStep.execute(any(AgentContext.class), any(PrContext.class)))
			.willReturn(RebaseResult.clean("fix/branch"));
		given(this.runTests.execute(any(AgentContext.class), any(ConflictReport.class)))
			.willReturn(new BuildResult(true, false, List.of("mcp-spring-webflux"), "BUILD SUCCESS", 5000));

		PrReviewWorkflow workflow = createWorkflow(true);
		Path report = workflow.execute(5774);

		assertThat(report).exists();
		verify(this.agentClient, never()).run(anyString());
	}

	@Test
	void pipeline_versionPatternFails_skipsAiAssessment() {
		PrContext prWithJavax = new PrContext(
				5774, "Add javax import", "This PR adds javax.servlet import", "author", List.of(), "open", "main",
				"fix/javax", List.of(new io.github.markpollack.prreview.model.FileChange("src/main/java/App.java",
						"modified", 5, 0, "+import javax.servlet.http.HttpServletRequest;")),
				List.of(), List.of(), List.of());

		GitHubRestClient gitHub = mock(GitHubRestClient.class);
		given(gitHub.fetchPrContext(anyInt())).willReturn(prWithJavax);
		FetchPrContextStep fetchStep = new FetchPrContextStep(gitHub);

		given(this.rebaseStep.execute(any(AgentContext.class), any(PrContext.class)))
			.willReturn(RebaseResult.clean("fix/javax"));
		given(this.runTests.execute(any(AgentContext.class), any(ConflictReport.class)))
			.willReturn(new BuildResult(true, false, List.of("module"), "BUILD SUCCESS", 3000));

		PrReviewWorkflow workflow = new PrReviewWorkflow(fetchStep, this.rebaseStep, this.conflictDetection,
				this.runTests, new BuildJudge(), new VersionPatternJudge(), this.assessCodeQuality, this.assessBackport,
				new QualityJudge(this.agentClient), this.generateReport,
				new WorkshopProperties(5774, false, this.tempDir.toString()));
		Path report = workflow.execute(5774);

		assertThat(report).exists();
		verify(this.agentClient, never()).run(anyString());
		String content = readFile(report);
		assertThat(content).contains("FAIL");
	}

	private PrReviewWorkflow createWorkflow(boolean skipAi) {
		return new PrReviewWorkflow(this.fetchPrContext, this.rebaseStep, this.conflictDetection, this.runTests,
				new BuildJudge(), new VersionPatternJudge(), this.assessCodeQuality, this.assessBackport,
				new QualityJudge(this.agentClient), this.generateReport,
				new WorkshopProperties(5774, skipAi, this.tempDir.toString()));
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
