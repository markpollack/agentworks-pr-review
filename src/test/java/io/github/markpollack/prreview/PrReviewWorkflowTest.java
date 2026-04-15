package io.github.markpollack.prreview;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.markpollack.journal.Journal;
import io.github.markpollack.journal.event.CustomEvent;
import io.github.markpollack.journal.event.JournalEvent;
import io.github.markpollack.journal.event.LLMCallEvent;
import io.github.markpollack.journal.event.StateChangeEvent;
import io.github.markpollack.journal.storage.InMemoryStorage;
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
import io.github.markpollack.prreview.steps.FixTestsStep;
import io.github.markpollack.prreview.steps.GenerateReportStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.workflow.core.AgentContext;
import org.junit.jupiter.api.AfterEach;
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

	private FixTestsStep fixTests;

	private AssessCodeQualityStep assessCodeQuality;

	private AssessBackportStep assessBackport;

	private GenerateReportStep generateReport;

	private AgentClient agentClient;

	private InMemoryStorage journalStorage;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		this.journalStorage = new InMemoryStorage();
		Journal.configure(this.journalStorage);

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
	}

	@AfterEach
	void tearDown() {
		Journal.reset();
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
		Path report = workflow.handle(AgentContext.create(), 5774);

		assertThat(report).exists();
		String content = readFile(report);
		assertThat(content).contains("PR Review Report: #5774").contains("PASS — All judges approved");

		// Journal assertions
		String runId = workflow.lastRunId();
		assertThat(runId).isNotNull();
		List<JournalEvent> events = this.journalStorage.loadEvents("pr-review", runId);
		assertThat(events).isNotEmpty();

		// Phase 1 steps
		assertStepPair(events, "fetch-pr-context");
		assertStepPair(events, "rebase");
		assertStepPair(events, "conflict-detection");
		assertStepPair(events, "run-tests");

		// Phase transitions
		assertThat(stateChanges(events, "context-gathering", "judge-cascade")).hasSize(1);

		// T0 and T1 judge events
		assertJudgePair(events, "T0");
		assertJudgePair(events, "T1");

		// Phase 2 AI steps with LLMCallEvents
		assertStepPair(events, "assess-code-quality");
		assertStepPair(events, "assess-backport");
		assertThat(stateChanges(events, "judge-cascade", "ai-assessment")).hasSize(1);

		// T2 judge
		assertJudgePair(events, "T2");

		// Phase 3 report generation
		assertStepPair(events, "generate-report");
		assertThat(stateChanges(events, "ai-assessment", "report-generation")).hasSize(1);
	}

	@Test
	void pipeline_buildFails_skipsAiAssessment() {
		given(this.rebaseStep.execute(any(AgentContext.class), any(PrContext.class)))
			.willReturn(RebaseResult.conflict("fix/branch", List.of("pom.xml", "src/main/java/Config.java")));
		given(this.runTests.execute(any(AgentContext.class), any(ConflictReport.class)))
			.willReturn(BuildResult.skippedBuild());

		PrReviewWorkflow workflow = createWorkflow(false);
		Path report = workflow.handle(AgentContext.create(), 5774);

		assertThat(report).exists();
		verify(this.agentClient, never()).run(anyString());
		String content = readFile(report);
		assertThat(content).contains("FAIL — One or more judges flagged issues");

		// Journal: T0 FAIL means no T1, no AI steps
		String runId = workflow.lastRunId();
		List<JournalEvent> events = this.journalStorage.loadEvents("pr-review", runId);
		assertJudgePair(events, "T0");
		assertThat(judgeVerdictStatus(events, "T0")).isEqualTo("FAIL");
		assertThat(events.stream()
			.filter(e -> e instanceof CustomEvent ce && "judge-started".equals(ce.name())
					&& "T1".equals(ce.attributes().get("tier")))
			.count()).isZero();
	}

	@Test
	void pipeline_skipAi_skipsAiSteps() {
		given(this.rebaseStep.execute(any(AgentContext.class), any(PrContext.class)))
			.willReturn(RebaseResult.clean("fix/branch"));
		given(this.runTests.execute(any(AgentContext.class), any(ConflictReport.class)))
			.willReturn(new BuildResult(true, false, List.of("mcp-spring-webflux"), "BUILD SUCCESS", 5000));

		PrReviewWorkflow workflow = createWorkflow(true);
		Path report = workflow.handle(AgentContext.create(), 5774);

		assertThat(report).exists();
		verify(this.agentClient, never()).run(anyString());

		// Journal: T0 and T1 pass, but no AI step events
		String runId = workflow.lastRunId();
		List<JournalEvent> events = this.journalStorage.loadEvents("pr-review", runId);
		assertJudgePair(events, "T0");
		assertJudgePair(events, "T1");
		assertThat(events.stream()
			.filter(e -> e instanceof CustomEvent ce && "step-started".equals(ce.name())
					&& "assess-code-quality".equals(ce.attributes().get("step")))
			.count()).isZero();
	}

	@Test
	void pipeline_versionPatternFails_stillRunsAiAssessment() {
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
		given(this.agentClient.run(anyString())).willReturn(agentResponse("""
				{
				  "score": 0.7,
				  "status": "PASS",
				  "rationale": "Advisory review despite deprecated APIs",
				  "findings": ["Uses javax imports"]
				}
				"""));

		PrReviewWorkflow workflow = new PrReviewWorkflow(fetchStep, this.rebaseStep, this.conflictDetection,
				this.runTests, this.fixTests, new BuildJudge(), new VersionPatternJudge(), this.assessCodeQuality,
				this.assessBackport, new QualityJudge(this.agentClient), this.generateReport,
				new WorkshopProperties(5774, false, false, this.tempDir.toString(), "."));
		Path report = workflow.handle(AgentContext.create(), 5774);

		assertThat(report).exists();
		String content = readFile(report);
		assertThat(content).contains("FAIL");

		// Journal: T1 FAIL with version-pattern-finding events, but AI steps still run
		String runId = workflow.lastRunId();
		List<JournalEvent> events = this.journalStorage.loadEvents("pr-review", runId);
		assertJudgePair(events, "T1");
		assertThat(judgeVerdictStatus(events, "T1")).isEqualTo("FAIL");
		assertThat(events.stream()
			.filter(e -> e instanceof CustomEvent ce && "version-pattern-finding".equals(ce.name()))
			.count()).isGreaterThan(0);
		assertStepPair(events, "assess-code-quality");
		assertStepPair(events, "assess-backport");
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private PrReviewWorkflow createWorkflow(boolean skipAi) {
		return new PrReviewWorkflow(this.fetchPrContext, this.rebaseStep, this.conflictDetection, this.runTests,
				this.fixTests, new BuildJudge(), new VersionPatternJudge(), this.assessCodeQuality, this.assessBackport,
				new QualityJudge(this.agentClient), this.generateReport,
				new WorkshopProperties(5774, skipAi, false, this.tempDir.toString(), "."));
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

	private static void assertStepPair(List<JournalEvent> events, String stepName) {
		long started = events.stream()
			.filter(e -> e instanceof CustomEvent ce && "step-started".equals(ce.name())
					&& stepName.equals(ce.attributes().get("step")))
			.count();
		long completed = events.stream()
			.filter(e -> e instanceof CustomEvent ce && "step-completed".equals(ce.name())
					&& stepName.equals(ce.attributes().get("step")))
			.count();
		assertThat(started).as("step-started for %s", stepName).isEqualTo(1);
		assertThat(completed).as("step-completed for %s", stepName).isEqualTo(1);
	}

	private static void assertJudgePair(List<JournalEvent> events, String tier) {
		long started = events.stream()
			.filter(e -> e instanceof CustomEvent ce && "judge-started".equals(ce.name())
					&& tier.equals(ce.attributes().get("tier")))
			.count();
		long verdict = events.stream()
			.filter(e -> e instanceof CustomEvent ce && "judge-verdict".equals(ce.name())
					&& tier.equals(ce.attributes().get("tier")))
			.count();
		assertThat(started).as("judge-started for %s", tier).isEqualTo(1);
		assertThat(verdict).as("judge-verdict for %s", tier).isEqualTo(1);
	}

	private static String judgeVerdictStatus(List<JournalEvent> events, String tier) {
		return events.stream()
			.filter(e -> e instanceof CustomEvent ce && "judge-verdict".equals(ce.name())
					&& tier.equals(ce.attributes().get("tier")))
			.map(e -> (String) ((CustomEvent) e).attributes().get("status"))
			.findFirst()
			.orElse("");
	}

	private static List<StateChangeEvent> stateChanges(List<JournalEvent> events, String from, String to) {
		return events.stream()
			.filter(e -> e instanceof StateChangeEvent sce && from.equals(sce.fromState()) && to.equals(sce.toState()))
			.map(e -> (StateChangeEvent) e)
			.toList();
	}

}
