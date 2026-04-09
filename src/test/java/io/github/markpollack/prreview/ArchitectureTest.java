package io.github.markpollack.prreview;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "io.github.markpollack.prreview", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	private static final String STEPS = "io.github.markpollack.prreview.steps..";

	private static final String JUDGES = "io.github.markpollack.prreview.judges..";

	private static final String GITHUB = "io.github.markpollack.prreview.github..";

	private static final String MODEL = "io.github.markpollack.prreview.model..";

	private static final String CONFIG = "io.github.markpollack.prreview.config..";

	// --- Layered architecture ---

	@ArchTest
	static final ArchRule steps_should_not_depend_on_judges = noClasses().that()
		.resideInAPackage(STEPS)
		.should()
		.dependOnClassesThat()
		.resideInAPackage(JUDGES);

	@ArchTest
	static final ArchRule judges_should_not_depend_on_steps = noClasses().that()
		.resideInAPackage(JUDGES)
		.should()
		.dependOnClassesThat()
		.resideInAPackage(STEPS);

	@ArchTest
	static final ArchRule github_should_not_depend_on_steps = noClasses().that()
		.resideInAPackage(GITHUB)
		.should()
		.dependOnClassesThat()
		.resideInAPackage(STEPS);

	@ArchTest
	static final ArchRule github_should_not_depend_on_judges = noClasses().that()
		.resideInAPackage(GITHUB)
		.should()
		.dependOnClassesThat()
		.resideInAPackage(JUDGES);

	@ArchTest
	static final ArchRule model_should_not_depend_on_steps = noClasses().that()
		.resideInAPackage(MODEL)
		.should()
		.dependOnClassesThat()
		.resideInAPackage(STEPS);

	@ArchTest
	static final ArchRule model_should_not_depend_on_judges = noClasses().that()
		.resideInAPackage(MODEL)
		.should()
		.dependOnClassesThat()
		.resideInAPackage(JUDGES);

	@ArchTest
	static final ArchRule model_should_not_depend_on_github = noClasses().that()
		.resideInAPackage(MODEL)
		.should()
		.dependOnClassesThat()
		.resideInAPackage(GITHUB);

	@ArchTest
	static final ArchRule config_should_not_depend_on_steps = noClasses().that()
		.resideInAPackage(CONFIG)
		.should()
		.dependOnClassesThat()
		.resideInAPackage(STEPS);

	@ArchTest
	static final ArchRule config_should_not_depend_on_judges = noClasses().that()
		.resideInAPackage(CONFIG)
		.should()
		.dependOnClassesThat()
		.resideInAPackage(JUDGES);

	// --- Naming conventions ---

	@ArchTest
	static final ArchRule steps_should_be_named_step = classes().that()
		.resideInAPackage(STEPS)
		.and()
		.areTopLevelClasses()
		.and()
		.arePublic()
		.and()
		.areNotInterfaces()
		.and()
		.haveSimpleNameNotEndingWith("package-info")
		.should()
		.haveSimpleNameEndingWith("Step")
		.allowEmptyShould(true);

	@ArchTest
	static final ArchRule judges_should_be_named_judge = classes().that()
		.resideInAPackage(JUDGES)
		.and()
		.areTopLevelClasses()
		.and()
		.areNotInterfaces()
		.and()
		.haveSimpleNameNotEndingWith("package-info")
		.should()
		.haveSimpleNameEndingWith("Judge")
		.allowEmptyShould(true);

	// --- No cycles ---

	@ArchTest
	static final ArchRule no_package_cycles = slices().matching("io.github.markpollack.prreview.(*)..")
		.should()
		.beFreeOfCycles();

}
