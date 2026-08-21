package io.github.markpollack.prreview.steps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import io.github.markpollack.prreview.config.ReviewProperties;
import io.github.markpollack.prreview.config.WorkshopProperties;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.core.Description;
import io.github.markpollack.workflow.core.StepName;
import io.github.markpollack.workflow.flows.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.markpollack.agents.client.AgentClient;

import org.springframework.stereotype.Component;

/**
 * AI rebase/conflict-resolution step (DESIGN-ai-conflict-resolution.md, Stage 2). Runs
 * after {@link RebaseStep}; when the rebase conflicted and {@code review.auto-resolve} is
 * on, it re-does the rebase and resolves the conflicts with the agent instead of
 * escalating.
 *
 * <p>
 * Because {@link RebaseStep} <em>aborts</em> the rebase on conflict (leaving a clean
 * tree), this step re-runs the rebase itself so it can pause at the conflict and resolve:
 * <ol>
 * <li><b>Squash</b> (DD-2) — {@code git reset --soft <merge-base>} + commit, so the
 * rebase stops at at most one conflict point (single-pass, no {@code --continue}
 * loop).</li>
 * <li><b>Rebase</b> onto the base; clean → done.</li>
 * <li><b>Agent resolve</b> (DD-1, edit-in-place) — invoke the {@link AgentClient} to edit
 * the conflicted files, removing markers and merging both sides.</li>
 * <li><b>Verify</b> (DD-5) — zero conflict markers remain in every file, else escalate;
 * then {@code git add} + one {@code git rebase --continue}.</li>
 * </ol>
 * All-or-nothing (DD-4): any unresolved file / non-finishing {@code --continue} →
 * {@code git rebase --abort} + return the original conflict (escalate to the human path).
 * Input/output is {@link RebaseResult} so {@link ConflictDetectionStep} reclassifies the
 * resolved result (a successful resolve makes the downstream see a clean rebase).
 *
 * <p>
 * The git helpers are intentionally duplicated from {@link RebaseStep} for a
 * self-contained first implementation; extracting a shared {@code GitOperations} is
 * deferred to the generic-agent phase (see the design's open questions).
 */
@Component
@StepName("resolve-conflicts")
@Description("AI-resolves rebase conflicts (squash + rebase + agent edit-in-place + continue), else escalates")
public class ResolveConflictsStep implements Step<RebaseResult, RebaseResult> {

	private static final Logger logger = LoggerFactory.getLogger(ResolveConflictsStep.class);

	private final AgentClient agentClient;

	private final ReviewProperties reviewProperties;

	private Path workingDirectory;

	public ResolveConflictsStep(AgentClient agentClient, ReviewProperties reviewProperties,
			WorkshopProperties workshopProperties) {
		this.agentClient = agentClient;
		this.reviewProperties = reviewProperties;
		this.workingDirectory = Path.of(workshopProperties.repoDir());
	}

	public ResolveConflictsStep workingDirectory(Path workingDirectory) {
		this.workingDirectory = workingDirectory;
		return this;
	}

	@Override
	public String name() {
		return "resolve-conflicts";
	}

	@Override
	public RebaseResult execute(AgentContext ctx, RebaseResult input) {
		if (input.success()) {
			return input;
		}
		if (!this.reviewProperties.autoResolve()) {
			logger.info("Rebase conflict on {} and review.auto-resolve=false — escalating to human", input.branch());
			return input;
		}
		PrContext pr = ctx.require(FetchPrContextStep.PR_CONTEXT);
		return resolve(input, pr);
	}

	private RebaseResult resolve(RebaseResult input, PrContext pr) {
		String reviewBranch = input.branch();
		String base = pr.baseBranch();
		logger.info("Auto-resolving rebase conflict on {} ({} file(s)) via squash + rebase + agent", reviewBranch,
				input.conflictFiles().size());
		try {
			run("git", "checkout", "-f");
			run("git", "clean", "-fd");
			exec("git", "checkout", reviewBranch);

			// DD-2: squash the PR branch to one commit so the rebase stops at <=1
			// conflict.
			String mergeBase = run("git", "merge-base", "HEAD", base).stdout().trim();
			if (!mergeBase.isBlank()) {
				exec("git", "reset", "--soft", mergeBase);
				run("git", "commit", "--no-verify", "-m", "squash: PR #" + pr.number() + " for review");
			}

			// Rebase onto base; on conflict, PAUSE (do not abort) so we can resolve.
			ProcessResult rebase = run("git", "rebase", base);
			if (rebase.exitCode() == 0) {
				logger.info("Rebase clean after squash for {} — no resolution needed", reviewBranch);
				return RebaseResult.clean(reviewBranch);
			}

			List<String> conflicted = getConflictedFiles();
			if (conflicted.isEmpty()) {
				logger.warn("Rebase failed but no conflicted files found for {} — aborting + escalating", reviewBranch);
				run("git", "rebase", "--abort");
				return input;
			}

			if (!agentResolve(conflicted)) {
				logger.warn("Agent did not fully resolve {} conflict(s) on {} — aborting + escalating",
						conflicted.size(), reviewBranch);
				run("git", "rebase", "--abort");
				return input;
			}

			exec("git", "add", "-A");
			ProcessResult cont = run("git", "-c", "core.editor=true", "rebase", "--continue");
			if (cont.exitCode() != 0 || rebaseInProgress()) {
				logger.warn("git rebase --continue did not finish for {} (exit={}) — aborting + escalating",
						reviewBranch, cont.exitCode());
				run("git", "rebase", "--abort");
				return input;
			}

			logger.info("Auto-resolved {} conflict(s) on {}; rebase completed cleanly", conflicted.size(),
					reviewBranch);
			return RebaseResult.clean(reviewBranch);
		}
		catch (IOException | InterruptedException ex) {
			Thread.currentThread().interrupt();
			logger.error("Conflict resolution failed for {}: {}", reviewBranch, ex.getMessage());
			safeAbort();
			return input;
		}
	}

	/**
	 * Invokes the agent to edit the conflicted files in place (DD-1), then verifies that
	 * no conflict markers remain in any of them (DD-5).
	 */
	private boolean agentResolve(List<String> conflictedFiles) {
		try {
			this.agentClient.run(buildPrompt(conflictedFiles));
		}
		catch (Exception ex) {
			logger.warn("Agent invocation failed during conflict resolution: {}", ex.getMessage());
			return false;
		}
		for (String file : conflictedFiles) {
			if (hasConflictMarkers(file)) {
				logger.warn("Conflict markers still present in {} after agent resolution", file);
				return false;
			}
		}
		return true;
	}

	private String buildPrompt(List<String> files) {
		String fileList = files.stream().map(f -> "- " + f).collect(Collectors.joining("\n"));
		return """
				The git repository at %s is in the middle of a rebase with merge conflicts. \
				Resolve the conflicts in these files by editing them IN PLACE with the Edit tool:
				%s

				For each file: remove every Git conflict marker (<<<<<<<, =======, >>>>>>>) and \
				produce the correct merged content, keeping BOTH sides' intent where possible. \
				For documentation/config files (e.g. CLAUDE.md), merge both sets of changes rather \
				than discarding either. Do NOT run any git commands (no git add / rebase / commit) — \
				ONLY edit the file contents. When you finish, every listed file must contain zero \
				conflict markers.
				""".formatted(this.workingDirectory, fileList);
	}

	private boolean hasConflictMarkers(String relativePath) {
		try {
			Path file = this.workingDirectory.resolve(relativePath);
			if (!Files.exists(file)) {
				return false;
			}
			String content = Files.readString(file);
			return content.contains("<<<<<<<") || content.contains("=======") || content.contains(">>>>>>>");
		}
		catch (IOException ex) {
			return true;
		}
	}

	private boolean rebaseInProgress() {
		return Files.exists(this.workingDirectory.resolve(".git/rebase-merge"))
				|| Files.exists(this.workingDirectory.resolve(".git/rebase-apply"));
	}

	private void safeAbort() {
		try {
			run("git", "rebase", "--abort");
		}
		catch (IOException | InterruptedException ex) {
			Thread.currentThread().interrupt();
			logger.warn("Failed to abort rebase during cleanup: {}", ex.getMessage());
		}
	}

	private List<String> getConflictedFiles() throws IOException, InterruptedException {
		ProcessResult result = run("git", "diff", "--name-only", "--diff-filter=U");
		if (result.exitCode() != 0 || result.stdout().isBlank()) {
			return List.of();
		}
		return result.stdout().lines().filter(line -> !line.isBlank()).collect(Collectors.toList());
	}

	private void exec(String... command) throws IOException, InterruptedException {
		ProcessResult result = run(command);
		if (result.exitCode() != 0) {
			throw new IOException("Command failed (exit " + result.exitCode() + "): " + String.join(" ", command) + "\n"
					+ result.stderr());
		}
	}

	private ProcessResult run(String... command) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(command).directory(this.workingDirectory.toFile())
			.redirectErrorStream(false);
		Process process = pb.start();
		String stdout;
		String stderr;
		try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
			stdout = outReader.lines().collect(Collectors.joining("\n"));
			stderr = errReader.lines().collect(Collectors.joining("\n"));
		}
		int exitCode = process.waitFor();
		return new ProcessResult(exitCode, stdout, stderr);
	}

	private record ProcessResult(int exitCode, String stdout, String stderr) {
	}

	@Override
	public AgentContext updateContext(AgentContext ctx, RebaseResult output) {
		return ctx.mutate().with(RebaseStep.REBASE_RESULT, output).build();
	}

	@Override
	public Class<?> inputType() {
		return RebaseResult.class;
	}

	@Override
	public Class<?> outputType() {
		return RebaseResult.class;
	}

}
