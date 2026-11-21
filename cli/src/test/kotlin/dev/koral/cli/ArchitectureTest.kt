package dev.koral.cli

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ArchitectureTest {
    private val classes = ClassFileImporter().importPackages("dev.koral")

    private fun isolated(
        from: String,
        vararg forbidden: String,
    ) = noClasses()
        .that()
        .resideInAPackage(from)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(*forbidden)

    @Test
    fun productModulesKeepTheirDirection() {
        isolated("dev.koral.compiler..", "dev.koral.cli..", "dev.koral.toolchain..").check(classes)
        isolated("dev.koral.api..", "dev.koral.compiler..", "dev.koral.cli..", "dev.koral.toolchain..").check(classes)
        isolated("dev.koral.toolchain..", "dev.koral.compiler..", "dev.koral.cli..", "dev.koral.api..").check(classes)
        isolated(
            "dev.koral.compiler.model..",
            "dev.koral.compiler.frontend..",
            "dev.koral.compiler.backend..",
            "dev.koral.compiler.checks..",
            "dev.koral.compiler.metadata..",
        ).check(classes)
        isolated(
            "dev.koral.compiler.checks..",
            "dev.koral.compiler.frontend..",
            "dev.koral.compiler.backend..",
            "dev.koral.compiler.metadata..",
        ).check(classes)
        isolated("dev.koral.compiler.backend..", "dev.koral.compiler.frontend..", "dev.koral.compiler.metadata..").check(classes)
        isolated("dev.koral.compiler.metadata..", "dev.koral.compiler.frontend..", "dev.koral.compiler.backend..").check(classes)
    }

    @Test
    fun dependencyDetectionActuallyRejectsAForbiddenEdge() {
        // The CLI really imports the compiler. Reversing that rule must fail.
        assertFailsWith<AssertionError> {
            isolated("dev.koral.cli..", "dev.koral.compiler..").check(classes)
        }
    }
}
