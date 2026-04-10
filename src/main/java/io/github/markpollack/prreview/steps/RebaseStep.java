package io.github.markpollack.prreview.steps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.github.markpollack.prreview.config.WorkshopProperties;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.model.RebaseResult;
import io.github.markpollack.workflow.flows.AgentContext;
import io.github.markpollack.workflow.flows.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Rebases the PR branch onto main to check for conflicts.
 *
 * <p>
 * Executes: {@code git fetch origin pull/{N}/head:{branch}} then {@code git rebase main}.
 * Returns a {@link RebaseResult} indicating success or listing conflicted files.
 */
@Component
public class RebaseStep implements Step<PrContext, RebaseResult> {

	private static final Logger logger = LoggerFactory.getLogger(RebaseStep.class);

	private Path workingDirectory;

	public RebaseStep(WorkshopProperties workshopProperties) {
		this.workingDirectory = Path.of(workshopProperties.repoDir());
	}

	public RebaseStep workingDirectory(Path workingDirectory) {
		this.workingDirectory = workingDirectory;
		return this;
	}

	@Override
	public String name() {
		return "rebase-on-main";
	}

	@Override
	public RebaseResult execute(AgentContext ctx, PrContext input) {
		String branch = input.headBranch();
		String reviewBranch = "review/pr-" + input.number();
		int prNumber = input.number();
		logger.info("Rebasing PR #{} branch '{}' onto '{}'", prNumber, branch, input.baseBranch());

		try {
			// Ensure we're on the base branch before fetching
			exec("git", "checkout", input.baseBranch());
			exec("git", "pull", "--ff-only", "origin", input.baseBranch());

			// Delete any stale review branch from a previous run
			run("git", "branch", "-D", reviewBranch);

			// Fetch the PR into a review-specific local branch
			exec("git", "fetch", "origin", "pull/" + prNumber + "/head:" + reviewBranch);

			// Checkout the review branch
			exec("git", "checkout", reviewBranch);

			// Attempt rebase onto base branch
			ProcessResult rebaseResult = run("git", "rebase", input.baseBranch());

			if (rebaseResult.exitCode() == 0) {
				logger.info("Rebase clean for PR #{}", prNumber);
				return RebaseResult.clean(reviewBranch);
			}

			// Rebase failed — collect conflicted files
			List<String> conflictFiles = getConflictedFiles();
			logger.warn("Rebase conflict in PR #{}: {} files", prNumber, conflictFiles.size());

			// Abort the failed rebase to leave repo clean
			run("git", "rebase", "--abort");

			return RebaseResult.conflict(reviewBranch, conflictFiles);
		}
		catch (IOException | InterruptedException ex) {
			Thread.currentThread().interrupt();
			logger.error("Rebase failed for PR #{}: {}", prNumber, ex.getMessage());
			return new RebaseResult(false, reviewBranch, List.of(), "Rebase error: " + ex.getMessage());
		}
	}

	/**
	 * Cleans up the review branch and restores the base branch. Call this after tests
	 * complete.
	 */
	public void cleanup(PrContext input) {
		String reviewBranch = "review/pr-" + input.number();
		try {
			run("git", "checkout", input.baseBranch());
			run("git", "branch", "-D", reviewBranch);
			logger.info("Cleaned up review branch {}", reviewBranch);
		}
		catch (IOException | InterruptedException ex) {
			logger.warn("Failed to clean up review branch: {}", ex.getMessage());
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

	ProcessResult run(String... command) throws IOException, InterruptedException {
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

	record ProcessResult(int exitCode, String stdout, String stderr) {
	}

	@Override
	public Class<?> inputType() {
		return PrContext.class;
	}

	@Override
	public Class<?> outputType() {
		return RebaseResult.class;
	}

}
