package io.github.bluething.atmsimulator;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.github.bluething.atmsimulator.domain.BusinessException;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Keeps the dependency direction of the ports-and-adapters layout honest:
 *
 * <pre>
 *   cli ──────────┐
 *                 ▼
 *   infrastructure ──► application ──► domain
 * </pre>
 *
 * Only the composition root ({@link AtmApplication}) may see every layer.
 */
@AnalyzeClasses(packagesOf = AtmApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "io.github.bluething.atmsimulator";

    @ArchTest
    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy(ROOT + ".domain..")
            .layer("Application").definedBy(ROOT + ".application..")
            .layer("Infrastructure").definedBy(ROOT + ".infrastructure..")
            .layer("Cli").definedBy(ROOT + ".cli..")
            .layer("CompositionRoot").definedBy(ROOT)
            .whereLayer("CompositionRoot").mayNotBeAccessedByAnyLayer()
            .whereLayer("Cli").mayOnlyBeAccessedByLayers("CompositionRoot")
            .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("CompositionRoot")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Cli", "Infrastructure", "CompositionRoot")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Cli", "Infrastructure");

    @ArchTest
    static final ArchRule domainUsesOnlyTheJdk = classes()
            .that().resideInAPackage(ROOT + ".domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(ROOT + ".domain..", "java..");

    /** Customer-facing rejections are business decisions, so they come from the core only. */
    @ArchTest
    static final ArchRule businessExceptionsComeFromTheCore = classes()
            .that().areAssignableTo(BusinessException.class)
            .should().resideInAnyPackage(ROOT + ".domain..", ROOT + ".application..");
}
