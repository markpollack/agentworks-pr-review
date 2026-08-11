package io.github.markpollack.prreview.v3;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.github.markpollack.judge.jury.ConsensusStrategy;
import io.github.markpollack.judge.jury.Jury;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.prreview.config.WorkshopProperties;
import io.github.markpollack.prreview.dsl.AssembleEarlyReportStep;
import io.github.markpollack.prreview.dsl.AssembleReportStep;
import io.github.markpollack.prreview.dsl.CleanupStep;
import io.github.markpollack.prreview.dsl.QualityJudgeStep;
import io.github.markpollack.prreview.dsl.VersionPatternStep;
import io.github.markpollack.prreview.judges.BuildJudge;
import io.github.markpollack.prreview.judges.QualityJudge;
import io.github.markpollack.prreview.judges.VersionPatternJudge;
import io.github.markpollack.prreview.model.FixPolicy;
import io.github.markpollack.prreview.steps.AssessBackportStep;
import io.github.markpollack.prreview.steps.AssessCodeQualityStep;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.FixTestsStep;
import io.github.markpollack.prreview.steps.GenerateReportStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.prreview.steps.ShouldAttemptFixStep;
import io.github.markpollack.workflow.flows.v3.Step;
import io.github.markpollack.workflow.flows.v3.JuryProvider;
import io.github.markpollack.workflow.flows.v3.WorkflowEmission;
import io.github.markpollack.workflow.spec.v3.WorkflowSpec;
import io.github.markpollack.workflow.spec.v3.catalog.WorkflowCatalogSelector;
import io.github.markpollack.workflow.spec.v3.envelope.CuratedCatalog;
import io.github.markpollack.workflow.spec.v3.envelope.WorkflowArtifactRecord;
import io.github.markpollack.workflow.spec.v3.envelope.WorkflowCatalogSelection;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the v3 authoring surface: the leaves the pipeline is composed of, the jury
 * its gate is scored by, and the {@link WorkflowSpec} they add up to.
 *
 * <p>
 * The leaf beans here are unconditional. They used to live in {@code DslWorkflowConfig}
 * behind {@code workshop.use-dsl}, which tied the existence of a step to the choice of
 * <em>executor</em> — a workflow's leaves do not stop existing because a different runner
 * is selected, and the v3 spec has to be emittable either way.
 *
 * <p>
 * The {@link WorkflowSpec} bean is the wiring's own test: {@code Workflow.build()}
 * refuses a graph whose bindings it cannot derive, so a leaf whose declared types stop
 * fitting the graph fails application startup rather than emitting a wrong document.
 */
@Configuration(proxyBeanMethods = false)
public class PrReviewV3Config {

	@Bean
	VersionPatternStep dslVersionPatternStep(VersionPatternJudge versionPatternJudge) {
		return new VersionPatternStep(versionPatternJudge);
	}

	@Bean
	QualityJudgeStep qualityJudgeStep(QualityJudge qualityJudge) {
		return new QualityJudgeStep(qualityJudge);
	}

	@Bean
	CleanupStep cleanupStep(RebaseStep rebaseStep) {
		return new CleanupStep(rebaseStep);
	}

	@Bean
	AssembleReportStep assembleReportStep() {
		return new AssembleReportStep();
	}

	@Bean
	AssembleEarlyReportStep assembleEarlyReportStep() {
		return new AssembleEarlyReportStep();
	}

	@Bean
	GenerateReportV3Step generateReportV3Step(GenerateReportStep generateReportStep) {
		return new GenerateReportV3Step(generateReportStep);
	}

	/**
	 * The jury the build-health gate is scored by — one tier, the deterministic
	 * {@link BuildJudge}, which is exactly what the v1 {@code BuildGate} delegated to.
	 *
	 * <p>
	 * The gate's <em>name</em> is authored at the call site rather than read off this
	 * object's class, and this bean is why: a real jury is assembled, not subclassed, so
	 * a class-name derivation would emit {@code judge:pr-review.simple:v1} — a valid spec
	 * naming the wrong capability. What the surface takes from the bean is its type, and
	 * that is what supplies the {@code judge:} prefix.
	 */
	@Bean
	Jury buildHealthJury(BuildJudge buildJudge) {
		return SimpleJury.builder().votingStrategy(new ConsensusStrategy()).judge(buildJudge).build();
	}

	@Bean
	JuryProvider buildHealthJuryProvider(Jury buildHealthJury) {
		return JuryProvider.of("build-health-jury", "pr-review.build-health-jury", 1, buildHealthJury);
	}

	/**
	 * The fix policy as the artifact carries it. {@code workshop.fix-tests} is a
	 * deployment property; making it the decision node's {@code config} puts it inside
	 * {@code specHash}, so turning AI repairs on produces a different spec instead of the
	 * same spec behaving differently.
	 */
	@Bean
	FixPolicy fixPolicy(WorkshopProperties workshopProperties) {
		return workshopProperties.fixTests() ? FixPolicy.attemptingFixes() : FixPolicy.skippingFixes();
	}

	@Bean
	WorkflowEmission prReviewEmissionV3(FetchPrContextStep fetchPrContext, RebaseStep rebaseOnMain,
			ConflictDetectionStep detectConflicts, RunTestsStep runTests, ShouldAttemptFixStep shouldAttemptFix,
			FixTestsStep fixTests, CleanupStep cleanupBranch, VersionPatternStep versionPatternCheck,
			AssessCodeQualityStep assessCodeQuality, AssessBackportStep assessBackport, QualityJudgeStep qualityJudge,
			AssembleReportStep assembleReport, AssembleEarlyReportStep assembleEarlyReport,
			GenerateReportV3Step generateReport, JuryProvider buildHealthJuryProvider, FixPolicy fixPolicy) {

		return PrReviewWorkflowV3.build(fetchPrContext, rebaseOnMain, detectConflicts, runTests, shouldAttemptFix,
				fixTests, cleanupBranch, versionPatternCheck, assessCodeQuality, assessBackport, qualityJudge,
				assembleReport, assembleEarlyReport, generateReport, buildHealthJuryProvider, fixPolicy);
	}

	@Bean
	WorkflowSpec prReviewSpecV3(WorkflowEmission prReviewEmissionV3) {
		return prReviewEmissionV3.spec();
	}

	@Bean
	WorkflowArtifactRecord prReviewArtifactRecordV3(WorkflowEmission prReviewEmissionV3) {
		return prReviewEmissionV3.artifactRecord();
	}

	/**
	 * What this deployment deploys (CONTRACT §13.2), derived from the leaves themselves.
	 *
	 * <p>
	 * The leaves arrive as every {@code Step} bean in the container rather than as
	 * fourteen parameters, and are matched to the spec's operation aliases by name —
	 * which is the same identity the emitter used. A leaf the spec names and the
	 * container does not have fails here rather than serving a consumer a catalog that
	 * quietly omits an operation.
	 */
	@Bean
	CuratedCatalog prReviewCatalogV3(WorkflowSpec prReviewSpecV3, List<Step<?, ?>> leaves,
			List<JuryProvider> juryProviders) {
		Map<String, Step<?, ?>> byName = new TreeMap<>();
		for (Step<?, ?> leaf : leaves) {
			Step<?, ?> clash = byName.put(leaf.name(), leaf);
			if (clash != null) {
				throw new IllegalStateException("two beans claim the operation name '" + leaf.name() + "': "
						+ clash.getClass().getName() + " and " + leaf.getClass().getName());
			}
		}
		Map<String, JuryProvider> juriesByAlias = new TreeMap<>();
		for (JuryProvider jury : juryProviders) {
			JuryProvider clash = juriesByAlias.put(jury.alias(), jury);
			if (clash != null) {
				throw new IllegalStateException("two beans claim the jury alias '" + jury.alias() + "'");
			}
		}
		return CuratedCatalogFactory.from(prReviewSpecV3, byName, juriesByAlias);
	}

	@Bean
	WorkflowCatalogSelection prReviewCatalogSelectionV3(CuratedCatalog prReviewCatalogV3) {
		return new WorkflowCatalogSelector().select(prReviewCatalogV3, "pr-review");
	}

}
