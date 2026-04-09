package io.github.markpollack.prreview.judges;

import java.util.List;

import io.github.markpollack.prreview.model.FileChange;
import io.github.markpollack.prreview.model.PrContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Check;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;

class VersionPatternJudgeTest {

	private final VersionPatternJudge judge = new VersionPatternJudge();

	private JudgmentContext contextWithPatch(String filename, String patch) {
		PrContext pr = new PrContext(1, "Test PR", null, "author", List.of(), "open", "main", "feature",
				List.of(new FileChange(filename, "modified", 10, 5, patch)), List.of(), List.of(), List.of());
		return JudgmentContext.builder()
			.goal("Version pattern check")
			.metadata(VersionPatternJudge.PR_CONTEXT, pr)
			.build();
	}

	private JudgmentContext contextWithFiles(List<FileChange> files) {
		PrContext pr = new PrContext(1, "Test PR", null, "author", List.of(), "open", "main", "feature", files,
				List.of(), List.of(), List.of());
		return JudgmentContext.builder()
			.goal("Version pattern check")
			.metadata(VersionPatternJudge.PR_CONTEXT, pr)
			.build();
	}

	@Nested
	class CleanDiffs {

		@Test
		void shouldPassWhenNoPatternsMatched() {
			JudgmentContext ctx = contextWithPatch("App.java",
					"+import jakarta.servlet.http.HttpServletRequest;\n+import org.springframework.web.bind.annotation.GetMapping;");

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(judgment.pass()).isTrue();
			assertThat(judgment.reasoning()).contains("no migration anti-patterns");
		}

		@Test
		void shouldPassWhenNoPrContextAvailable() {
			JudgmentContext ctx = JudgmentContext.builder().goal("No context").build();

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
			assertThat(judgment.reasoning()).contains("skipping");
		}

		@Test
		void shouldPassWhenPatchIsNull() {
			JudgmentContext ctx = contextWithPatch("README.md", null);

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		}

	}

	@Nested
	class JavaxImports {

		@Test
		void shouldDetectJavaxImport() {
			JudgmentContext ctx = contextWithPatch("Service.java", "+import javax.inject.Inject;");

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(failedCheckIds(judgment)).contains("javax-imports");
		}

		@Test
		void shouldIgnoreRemovedJavaxImport() {
			JudgmentContext ctx = contextWithPatch("Service.java", "-import javax.inject.Inject;");

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
		}

	}

	@Nested
	class JacksonPackage {

		@Test
		void shouldDetectJackson2xImport() {
			JudgmentContext ctx = contextWithPatch("Config.java",
					"+import com.fasterxml.jackson.databind.ObjectMapper;");

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(failedCheckIds(judgment)).contains("jackson-2x");
		}

	}

	@Nested
	class MockBean {

		@Test
		void shouldDetectMockBeanAnnotation() {
			JudgmentContext ctx = contextWithPatch("MyTest.java", "+    @MockBean\n+    private MyService service;");

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(failedCheckIds(judgment)).contains("mock-bean");
		}

	}

	@Nested
	class MockMvcLegacy {

		@Test
		void shouldDetectMockMvcRequestBuilders() {
			JudgmentContext ctx = contextWithPatch("ControllerTest.java",
					"+import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;");

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(failedCheckIds(judgment)).contains("mock-mvc-legacy");
		}

	}

	@Nested
	class WebSecurityConfigurer {

		@Test
		void shouldDetectWebSecurityConfigurerAdapter() {
			JudgmentContext ctx = contextWithPatch("SecurityConfig.java",
					"+public class SecurityConfig extends WebSecurityConfigurerAdapter {");

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(failedCheckIds(judgment)).contains("web-security-configurer");
		}

	}

	@Nested
	class MultipleFindings {

		@Test
		void shouldReportAllFindingsAcrossFiles() {
			List<FileChange> files = List.of(
					new FileChange("A.java", "modified", 5, 2, "+import javax.servlet.Filter;"),
					new FileChange("B.java", "modified", 3, 1, "+import com.fasterxml.jackson.databind.JsonNode;"));

			JudgmentContext ctx = contextWithFiles(files);
			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
			assertThat(failedCheckIds(judgment)).containsExactlyInAnyOrder("javax-imports", "jackson-2x");
			assertThat(judgment.reasoning()).contains("2 anti-patterns");
		}

		@Test
		void shouldUseSingularForOneAntiPattern() {
			JudgmentContext ctx = contextWithPatch("A.java", "+import javax.servlet.Filter;");

			Judgment judgment = judge.judge(ctx);

			assertThat(judgment.reasoning()).contains("1 anti-pattern found");
		}

	}

	private static List<String> failedCheckIds(Judgment judgment) {
		return judgment.checks().stream().filter(c -> !c.passed()).map(Check::name).toList();
	}

}
