package com.hotel.converter

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class ArchitectureTest {
    private val importedClasses =
        ClassFileImporter()
            .importPackages("com.hotel.converter")

    @Test
    fun domainModelsMustNotDependOnKtorFramework() {
        val rule =
            noClasses()
                .that().haveSimpleNameEndingWith("Draft")
                .or().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("io.ktor..", "io.netty..")
                .because("Canonical reservation models must remain pure and transport/HTTP agnostic")

        rule.check(importedClasses)
    }

    @Test
    fun applicationCodeMustNotCallSystemExitDirectly() {
        val rule =
            noClasses()
                .should().callMethod(System::class.java, "exit", Int::class.javaPrimitiveType)
                .because("Direct System.exit() calls are prohibited in the converter service")

        rule.check(importedClasses)
    }
}
