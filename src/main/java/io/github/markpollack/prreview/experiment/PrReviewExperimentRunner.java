package io.github.markpollack.prreview.experiment;

import java.nio.file.Path;

import io.github.markpollack.journal.Journal;
import io.github.markpollack.journal.storage.JsonFileStorage;
import io.github.markpollack.prreview.config.GitHubProperties;
import io.github.markpollack.prreview.config.WorkshopProperties;
import io.github.markpollack.prreview.github.GitHubRestClient;
import io.github.markpollack.prreview.judges.BuildJudge;
import io.github.markpollack.prreview.judges.QualityJudge;
import io.github.markpollack.prreview.steps.AssessCodeQualityStep;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.GenerateReportStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.journal.WorkflowJournal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.markpollack.agents.claude.ClaudeAgentModel;
import io.github.markpollack.agents.client.AgentClient;

/**
 * Plain {@code main()} entry point that hand-wires {@link PrReviewExperimentWorkflow}
 * without Spring — the no-Spring sibling of {@code dsl.DslWorkflowConfig}.
 *
 * <p>
 * {@link #build(Path)} constructs the concrete collaborators (GitHub client,
 * deterministic context steps, a trace-wired {@link AgentClient}) and assembles the
 * workflow. {@link #main(String[])} configures the journal backend (done by Spring's
 * {@code JournalConfig} in the app) and runs a single review.
 *
 * <p>
 * <strong>Live run only.</strong> {@link #main} invokes Claude through the
 * {@code AgentClient} during {@code AssessCodeQualityStep}; it therefore needs the
 * {@code claude} CLI on {@code PATH}. When launched from inside a Claude Code session,
 * use {@code ~/scripts/claude-run.sh} so the nested process escapes the parent process
 * tree.
 */
public final class PrReviewExperimentRunner {

	private static final Logger logger = LoggerFactory.getLogger(PrReviewExperimentRunner.class);

	private static final String JOURNAL_DIR = "journal";

	private static final String TRACE_DIR = "traces";

	private static final String REPORT_DIR = "reports";

	private PrReviewExperimentRunner() {
	}

	public static void main(String[] args) {
		int prNumber = (args.length > 0) ? Integer.parseInt(args[0]) : 1;
		Path repoDir = Path.of(PrReviewExperimentWorkflow.DEFAULT_REPO_DIR);

		// Spring's JournalConfig does this via @PostConstruct; here we configure it
		// explicitly.
		Journal.configure(new JsonFileStorage(Path.of(JOURNAL_DIR)));
		WorkflowJournal.registerEventType();

		PrReviewExperimentWorkflow workflow = build(repoDir);

		logger.info("Reviewing {} PR #{} (repoDir={})", workflow.repo(), prNumber, repoDir);
		Path report = workflow.handle(AgentContext.create(), prNumber);
		logger.info("Report written to {}", report);
	}

	/**
	 * Assembles the workflow with concrete collaborators. Package-visible so it can be
	 * exercised without triggering a live Claude call.
	 * @param repoDir local clone of the target repository (rebase/test execution + Claude
	 * working directory)
	 * @return the assembled, ready-to-run workflow
	 */
	static PrReviewExperimentWorkflow build(Path repoDir) {
		// GitHub context (token optional — higher rate limits when present).
		GitHubProperties gitHubProps = new GitHubProperties(PrReviewExperimentWorkflow.DEFAULT_REPO,
				"https://api.github.com", System.getenv("GITHUB_TOKEN"));
		FetchPrContextStep fetchPrContext = new FetchPrContextStep(new GitHubRestClient(gitHubProps));

		// Deterministic context steps run against the local clone.
		WorkshopProperties workshop = new WorkshopProperties(1, false, JOURNAL_DIR, repoDir.toString(), false);
		RebaseStep rebaseStep = new RebaseStep(workshop);
		ConflictDetectionStep conflictDetection = new ConflictDetectionStep();
		RunTestsStep runTests = new RunTestsStep(workshop);

		// Trace-wired AI assessment: AgentClient over a Claude model with a JSONL trace
		// dir.
		ClaudeAgentModel model = ClaudeAgentModel.builder()
			.workingDirectory(repoDir)
			.traceDir(Path.of(TRACE_DIR))
			.build();
		AgentClient agentClient = AgentClient.create(model);

		AssessCodeQualityStep assessCodeQuality = new AssessCodeQualityStep(agentClient);
		// agent-experiment has no maintenance branches → no backport assessment is run.
		QualityJudge qualityJudge = new QualityJudge(agentClient, false);
		BuildJudge buildJudge = new BuildJudge();
		GenerateReportStep generateReport = new GenerateReportStep().outputDirectory(Path.of(REPORT_DIR));

		return new PrReviewExperimentWorkflow(fetchPrContext, rebaseStep, conflictDetection, runTests,
				assessCodeQuality, buildJudge, qualityJudge, generateReport);
	}

}
