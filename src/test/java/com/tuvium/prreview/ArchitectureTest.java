package com.tuvium.prreview;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.tuvium.prreview", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	// --- Layered architecture ---

	@ArchTest
	static final ArchRule steps_should_not_depend_on_judges = noClasses().that()
		.resideInAPackage("..steps..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("..judges..");

	@ArchTest
	static final ArchRule judges_should_not_depend_on_steps = noClasses().that()
		.resideInAPackage("..judges..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("..steps..");

	@ArchTest
	static final ArchRule github_should_not_depend_on_steps = noClasses().that()
		.resideInAPackage("..github..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("..steps..");

	@ArchTest
	static final ArchRule github_should_not_depend_on_judges = noClasses().that()
		.resideInAPackage("..github..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("..judges..");

	@ArchTest
	static final ArchRule model_should_not_depend_on_steps = noClasses().that()
		.resideInAPackage("..model..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("..steps..");

	@ArchTest
	static final ArchRule model_should_not_depend_on_judges = noClasses().that()
		.resideInAPackage("..model..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("..judges..");

	@ArchTest
	static final ArchRule model_should_not_depend_on_github = noClasses().that()
		.resideInAPackage("..model..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("..github..");

	@ArchTest
	static final ArchRule config_should_not_depend_on_steps = noClasses().that()
		.resideInAPackage("..config..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("..steps..");

	@ArchTest
	static final ArchRule config_should_not_depend_on_judges = noClasses().that()
		.resideInAPackage("..config..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("..judges..");

	// --- Naming conventions ---

	@ArchTest
	static final ArchRule steps_should_be_named_step = classes().that()
		.resideInAPackage("..steps..")
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
		.resideInAPackage("..judges..")
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
	static final ArchRule no_package_cycles = slices().matching("com.tuvium.prreview.(*)..").should().beFreeOfCycles();

}
