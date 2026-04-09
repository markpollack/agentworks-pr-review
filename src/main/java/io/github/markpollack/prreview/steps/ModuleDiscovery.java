package io.github.markpollack.prreview.steps;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.markpollack.prreview.model.FileChange;

/**
 * Discovers affected Maven modules from changed file paths.
 *
 * <p>
 * Maps file paths like {@code models/spring-ai-ollama/src/main/...} to module paths like
 * {@code models/spring-ai-ollama}. For files at the root (no module directory), returns
 * the root module {@code "."}.
 */
final class ModuleDiscovery {

	private ModuleDiscovery() {
	}

	/**
	 * Extracts unique Maven module paths from a list of changed files.
	 * <p>
	 * Uses the path segments before {@code src/} as the module path. Files without a
	 * {@code src/} directory (e.g., root pom.xml) map to the root module.
	 */
	static List<String> discoverModules(List<FileChange> files) {
		Set<String> modules = new LinkedHashSet<>();
		for (FileChange file : files) {
			modules.add(extractModule(file.filename()));
		}
		return List.copyOf(modules);
	}

	static String extractModule(String filePath) {
		int srcIndex = filePath.indexOf("/src/");
		if (srcIndex <= 0) {
			return ".";
		}
		return filePath.substring(0, srcIndex);
	}

}
