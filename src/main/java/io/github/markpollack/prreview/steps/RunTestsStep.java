package io.github.markpollack.prreview.steps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.github.markpollack.prreview.config.WorkshopProperties;
import io.github.markpollack.prreview.model.BuildResult;
import io.github.markpollack.prreview.model.ConflictReport;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Runs targeted Maven tests on affected modules.
 *
 * <p>
 * Skips if complex conflicts were detected. Otherwise, discovers affected modules from
 * the PR's changed files and runs {@code ./mvnw test -pl {modules} -am}.
 */
@Component
public class RunTestsStep implements Step<ConflictReport, BuildResult> {

	private static final Logger logger = LoggerFactory.getLogger(RunTestsStep.class);

	private static final int MAX_OUTPUT_LENGTH = 10_000;

	private Path workingDirectory;

	public RunTestsStep(WorkshopProperties workshopProperties) {
		this.workingDirectory = Path.of(workshopProperties.repoDir());
	}

	public RunTestsStep workingDirectory(Path workingDirectory) {
		this.workingDirectory = workingDirectory;
		return this;
	}

	@Override
	public String name() {
		return "run-tests";
	}

	@Override
	public BuildResult execute(AgentContext ctx, ConflictReport input) {
		if (input.hasComplexConflicts()) {
			logger.warn("Skipping tests — complex conflicts detected");
			return BuildResult.skippedBuild();
		}

		// Get PrContext from the shared context to discover affected modules
		PrContext prContext = ctx.require(FetchPrContextStep.PR_CONTEXT);
		List<String> modules = ModuleDiscovery.discoverModules(prContext.files());

		if (modules.isEmpty() || (modules.size() == 1 && ".".equals(modules.get(0)))) {
			logger.info("No specific modules affected, running full test suite");
			return runMaven(List.of("."));
		}

		logger.info("Running tests for affected modules: {}", modules);
		return runMaven(modules);
	}

	private BuildResult runMaven(List<String> modules) {
		List<String> command = buildMavenCommand(modules);
		logger.info("Executing: {}", String.join(" ", command));

		long startTime = System.currentTimeMillis();
		try {
			ProcessBuilder pb = new ProcessBuilder(command).directory(this.workingDirectory.toFile())
				.redirectErrorStream(true);
			Process process = pb.start();

			String output;
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				output = reader.lines().collect(Collectors.joining("\n"));
			}

			int exitCode = process.waitFor();
			long duration = System.currentTimeMillis() - startTime;

			String truncatedOutput = truncate(output);
			boolean success = (exitCode == 0);

			if (success) {
				logger.info("Build succeeded in {}ms for modules {}", duration, modules);
			}
			else {
				logger.warn("Build failed (exit {}) in {}ms for modules {}", exitCode, duration, modules);
			}

			return new BuildResult(success, false, modules, truncatedOutput, duration);
		}
		catch (IOException | InterruptedException ex) {
			Thread.currentThread().interrupt();
			long duration = System.currentTimeMillis() - startTime;
			logger.error("Build execution error: {}", ex.getMessage());
			return new BuildResult(false, false, modules, "Build error: " + ex.getMessage(), duration);
		}
	}

	static List<String> buildMavenCommand(List<String> modules) {
		List<String> command = new ArrayList<>();
		command.add("./mvnw");
		command.add("clean");
		command.add("test");
		command.add("-B");
		command.add("-Ddisable.checks=true");
		command.add("-Dmaven.build.cache.enabled=false");

		if (modules.size() == 1 && ".".equals(modules.get(0))) {
			return command;
		}

		command.add("-pl");
		command.add(String.join(",", modules));
		command.add("-am");

		return command;
	}

	private static String truncate(String output) {
		if (output.length() <= MAX_OUTPUT_LENGTH) {
			return output;
		}
		return output.substring(output.length() - MAX_OUTPUT_LENGTH);
	}

	@Override
	public Class<?> inputType() {
		return ConflictReport.class;
	}

	@Override
	public Class<?> outputType() {
		return BuildResult.class;
	}

}
