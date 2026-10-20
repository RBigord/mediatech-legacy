package com.mediatech.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * TP2 — étape 6. À copier dans src/test/java/com/mediatech/architecture/
 * Ces règles ne vérifient QUE le nouveau code (com.mediatech.emprunt) : les classes legacy
 * restées dans com.mediatech (réservations, adhérents, batch) ne sont pas concernées.
 */
@AnalyzeClasses(packages = "com.mediatech.emprunt", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule le_domaine_ne_depend_d_aucun_framework =
            noClasses().that().resideInAPackage("..emprunt.domaine..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "jakarta..", "com.fasterxml..", "java.sql..")
                    .because("le domaine est du Java pur");

    @ArchTest
    static final ArchRule l_application_ne_depend_d_aucun_framework =
            noClasses().that().resideInAPackage("..emprunt.application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "jakarta..", "java.sql..")
                    .because("les cas d'usage et les ports sont du Java pur");

    @ArchTest
    static final ArchRule le_coeur_ne_connait_pas_les_adapters =
            noClasses().that().resideInAnyPackage("..emprunt.domaine..", "..emprunt.application..")
                    .should().dependOnClassesThat().resideInAPackage("..emprunt.adapter..")
                    .because("toutes les dépendances pointent vers l'intérieur de l'hexagone");

    @ArchTest
    static final ArchRule les_adapters_ne_se_connaissent_pas =
            slices().matching("..emprunt.adapter.(*).(*)..")
                    .should().notDependOnEachOther()
                    .because("un adapter ne parle qu'aux ports, jamais à un autre adapter");
}
