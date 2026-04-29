package io.github.markpollack.prreview;

import io.github.markpollack.prreview.config.GitHubProperties;
import io.github.markpollack.prreview.config.WorkshopProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PreflightCheckTest {

	private static final WorkshopProperties WORKSHOP_PROPS = new WorkshopProperties(5774, false, ".", ".", false);

	@Test
	void extractJsonInt_findsRemainingInCoreSection() {
		String json = """
				{
				  "resources": {
				    "core": {
				      "limit": 60,
				      "remaining": 45,
				      "reset": 1234567890
				    }
				  }
				}
				""";

		assertThat(PreflightCheck.extractJsonInt(json, "remaining")).isEqualTo(45);
		assertThat(PreflightCheck.extractJsonInt(json, "limit")).isEqualTo(60);
	}

	@Test
	void extractJsonInt_missingKey_returnsNegativeOne() {
		assertThat(PreflightCheck.extractJsonInt("{}", "missing")).isEqualTo(-1);
	}

	@Test
	void checkResult_passFactory() {
		PreflightCheck.CheckResult result = PreflightCheck.CheckResult.pass("Test", "OK");
		assertThat(result.passed()).isTrue();
		assertThat(result.critical()).isTrue();
	}

	@Test
	void checkResult_criticalFailFactory() {
		PreflightCheck.CheckResult result = PreflightCheck.CheckResult.criticalFail("Test", "Bad");
		assertThat(result.passed()).isFalse();
		assertThat(result.critical()).isTrue();
	}

	@Test
	void checkResult_warnFailFactory() {
		PreflightCheck.CheckResult result = PreflightCheck.CheckResult.warnFail("Test", "Warn");
		assertThat(result.passed()).isFalse();
		assertThat(result.critical()).isFalse();
	}

	@Test
	void javaVersionCheck_passes() {
		// We're running on Java 21+, so this should always pass in our test env
		GitHubProperties props = new GitHubProperties("spring-projects/spring-ai", "https://api.github.com", null);
		PreflightCheck check = new PreflightCheck(props, WORKSHOP_PROPS);
		var results = check.run();

		PreflightCheck.CheckResult javaResult = results.stream()
			.filter(r -> "Java Version".equals(r.name()))
			.findFirst()
			.orElseThrow();
		assertThat(javaResult.passed()).isTrue();
	}

	@Test
	void gitCheck_passes() {
		GitHubProperties props = new GitHubProperties("spring-projects/spring-ai", "https://api.github.com", null);
		PreflightCheck check = new PreflightCheck(props, WORKSHOP_PROPS);
		var results = check.run();

		PreflightCheck.CheckResult gitResult = results.stream()
			.filter(r -> "Git".equals(r.name()))
			.findFirst()
			.orElseThrow();
		assertThat(gitResult.passed()).isTrue();
	}

}
