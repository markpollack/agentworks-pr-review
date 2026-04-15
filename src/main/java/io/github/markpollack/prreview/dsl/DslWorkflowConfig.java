package io.github.markpollack.prreview.dsl;

import io.github.markpollack.prreview.config.WorkshopProperties;
import io.github.markpollack.prreview.judges.BuildJudge;
import io.github.markpollack.prreview.judges.QualityJudge;
import io.github.markpollack.prreview.judges.VersionPatternJudge;
import io.github.markpollack.prreview.steps.AssessBackportStep;
import io.github.markpollack.prreview.steps.AssessCodeQualityStep;
import io.github.markpollack.prreview.steps.ConflictDetectionStep;
import io.github.markpollack.prreview.steps.FetchPrContextStep;
import io.github.markpollack.prreview.steps.FixTestsStep;
import io.github.markpollack.prreview.steps.GenerateReportStep;
import io.github.markpollack.prreview.steps.RebaseStep;
import io.github.markpollack.prreview.steps.RunTestsStep;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the DSL-based workflow. Only active when
 * {@code workshop.use-dsl=true}.
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
	AiAssessmentStep aiAssessmentStep(AssessCodeQualityStep assessCodeQuality, AssessBackportStep assessBackport) {
		return new AiAssessmentStep(assessCodeQuality, assessBackport);
	}

	@Bean
	AssembleReportStep assembleReportStep() {
		return new AssembleReportStep();
	}

	@Bean
	PrReviewDslWorkflow prReviewDslWorkflow(FetchPrContextStep fetchPrContext, RebaseStep rebaseStep,
			ConflictDetectionStep conflictDetection, RunTestsStep runTests, FixAndRetestStep fixAndRetestStep,
			CleanupStep cleanupStep, BuildGate buildGate, VersionPatternStep dslVersionPatternStep,
			AiAssessmentStep aiAssessmentStep, QualityJudgeStep qualityJudgeStep, AssembleReportStep assembleReportStep,
			GenerateReportStep generateReport, WorkshopProperties workshopProperties) {
		return new PrReviewDslWorkflow(fetchPrContext, rebaseStep, conflictDetection, runTests, fixAndRetestStep,
				cleanupStep, buildGate, dslVersionPatternStep, aiAssessmentStep, qualityJudgeStep, assembleReportStep,
				generateReport, workshopProperties);
	}

}
