package com.hotel.converter

import com.hotel.converter.adapter.ChannelAdapter
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
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
                .that().resideInAPackage("..domain..")
                .or().haveSimpleNameEndingWith("Draft")
                .should().dependOnClassesThat()
                .resideInAnyPackage("io.ktor..", "io.netty..")
                .because("Canonical reservation models must remain pure and transport/HTTP agnostic")

        rule.check(importedClasses)
    }

    @Test
    fun domainLayerMustNotDependOnAdapters() {
        val rule =
            noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..adapter..")
                .because("Domain layer must remain independent of specific channel adapters")

        rule.check(importedClasses)
    }

    @Test
    fun adaptersMustImplementChannelAdapterInterface() {
        val rule =
            classes()
                .that().resideInAPackage("..adapter..")
                .and().haveSimpleNameEndingWith("Adapter")
                .and().areNotInterfaces()
                .should().implement(ChannelAdapter::class.java)
                .because("All channel adapters must implement the ChannelAdapter port interface")

        rule.check(importedClasses)
    }

    @Test
    fun adaptersMustNotDependOnKtorFramework() {
        val rule =
            noClasses()
                .that().resideInAPackage("..adapter..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("io.ktor..", "io.netty..")
                .because("Adapters must be pure converters and agnostic of the HTTP transport framework")

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
