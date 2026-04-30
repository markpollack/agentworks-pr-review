package io.github.markpollack.prreview.dsl;

import java.nio.file.Path;

import io.github.markpollack.prreview.config.WorkshopProperties;
import io.github.markpollack.prreview.judges.BuildJudge;
import io.github.markpollack.prreview.judges.QualityJudge;
import io.github.markpollack.prreview.judges.VersionPatternJudge;
import io.github.markpollack.prreview.model.PrContext;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.FixTestsStep;
import io.github.markpollack.prreview.steps.GenerateReportStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;
import io.github.markpollack.workflow.flows.Step;
import io.github.markpollack.workflow.flows.workflow.Workflow;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the DSL-based workflow. Only active when
 * {@code workshop.use-dsl=true}.
 *
 * <p>
 * This class is the wiring hub: it assembles sub-workflows as Spring beans and injects
 * them into {@link PrReviewDslWorkflow} by phase, not by leaf step.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "workshop.use-dsl", havingValue = "true")
public class DslWorkflowConfig {

	@Bean
	BuildGate buildGate(BuildJudge buildJudge) {
		return new BuildGate(buildJudge);
	}

	@Bean
	VersionPatternStep dslVersionPatternStep(VersionPatternJudge versionPatternJudge) {
		return new VersionPatternStep(versionPatternJudge);
	}

	@Bean
	FixAndRetestStep fixAndRetestStep(FixTestsStep fixTestsStep, RunTestsStep runTestsStep,
			WorkshopProperties workshopProperties) {
		return new FixAndRetestStep(fixTestsStep, runTestsStep, workshopProperties);
	}

	@Bean
	CleanupStep cleanupStep(RebaseStep rebaseStep) {
		return new CleanupStep(rebaseStep);
	}

	@Bean
	QualityJudgeStep qualityJudgeStep(QualityJudge qualityJudge) {
		return new QualityJudgeStep(qualityJudge);
	}

	@Bean
	AssembleReportStep assembleReportStep() {
		return new AssembleReportStep();
	}

	@Bean
	Workflow<Integer, Object> contextPhase(FetchPrContextStep fetchPrContext, RebaseStep rebaseStep,
			ConflictDetectionStep conflictDetection, RunTestsStep runTests, FixAndRetestStep fixAndRetestStep,
			CleanupStep cleanupStep) {
		return Workflow.<Integer, Object>define("context-phase")
			.step(fetchPrContext)
			.then(rebaseStep)
			.then(conflictDetection)
			.then(runTests)
			.then(fixAndRetestStep)
			.then(cleanupStep)
			.build();
	}

	@Bean
	Workflow<Object, Object> aiAssessment(@Qualifier("assess-code-quality") Step<PrContext, ?> assessCodeQuality,
			@Qualifier("assess-backport") Step<PrContext, ?> assessBackport) {
		return Workflow.<Object, Object>define("ai-assessment")
			.step(new ExtractPrContextStep())
			.parallel(assessCodeQuality, assessBackport)
			.build();
	}

	@Bean
	Workflow<Object, Path> earlyReport(AssembleReportStep assembleReportStep, GenerateReportStep generateReport) {
		return Workflow.<Object, Path>define("early-report").step(assembleReportStep).then(generateReport).build();
	}

	@Bean
	Workflow<Object, Path> assessAndReport(VersionPatternStep dslVersionPatternStep,
			Workflow<Object, Object> aiAssessment, QualityJudgeStep qualityJudgeStep,
			AssembleReportStep assembleReportStep, GenerateReportStep generateReport) {
		return Workflow.<Object, Path>define("assess-and-report")
			.step(dslVersionPatternStep)
			.then(aiAssessment)
			.then(qualityJudgeStep)
			.then(assembleReportStep)
			.then(generateReport)
			.build();
	}

	@Bean
	PrReviewDslWorkflow prReviewDslWorkflow(Workflow<Integer, Object> contextPhase, BuildGate buildGate,
			@Qualifier("assessAndReport") Workflow<Object, Path> assessAndReport,
			@Qualifier("earlyReport") Workflow<Object, Path> earlyReport) {
		return new PrReviewDslWorkflow(contextPhase, buildGate, assessAndReport, earlyReport);
	}

}
