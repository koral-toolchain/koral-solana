package dev.koral.cli

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.StringWriter
import java.nio.file.Files
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArchitectureTest {
    private val boundaries =
        listOf(
            Boundary("api", "dev.koral.api", "dev.koral.api.Language"),
            Boundary("ir", "dev.koral.compiler.ir", "dev.koral.compiler.ir.CounterProgram"),
            Boundary("validation", "dev.koral.compiler.validation", "dev.koral.compiler.validation.ProgramChecks", setOf("ir")),
            Boundary("kotlin", "dev.koral.compiler.kotlin", "dev.koral.compiler.kotlin.KotlinCompiler", setOf("ir", "validation")),
            Boundary("target", "dev.koral.compiler.target", "dev.koral.compiler.target.c.CounterC", setOf("ir", "validation")),
            Boundary("abi", "dev.koral.compiler.abi", "dev.koral.compiler.abi.ProgramAbi", setOf("ir", "validation")),
            Boundary("toolchain", "dev.koral.toolchain", "dev.koral.toolchain.SbfToolchain"),
            Boundary(
                "pipeline",
                "dev.koral.pipeline",
                "dev.koral.pipeline.CompilerPipeline",
                setOf("api", "ir", "validation", "kotlin", "target", "abi", "toolchain"),
            ),
            Boundary("cli", "dev.koral.cli", "dev.koral.cli.MainKt", setOf("pipeline", "toolchain")),
        )

    private data class Boundary(
        val id: String,
        val pkg: String,
        val example: String,
        val allowed: Set<String> = emptySet(),
    )

    private fun forbidden(from: Boundary) = boundaries.filter { it.id != from.id && it.id !in from.allowed }

    private fun rule(from: Boundary) =
        noClasses()
            .that()
            .resideInAPackage(from.pkg + "..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(*forbidden(from).map { it.pkg + ".." }.toTypedArray())

    @Test
    fun everyProductionClassHasOneBoundaryAndAllowedDependencies() {
        val classes = ClassFileImporter().withImportOption(ImportOption.DoNotIncludeTests()).importPackages("dev.koral")
        classes.forEach { type ->
            assertEquals(1, boundaries.count { type.packageName == it.pkg || type.packageName.startsWith(it.pkg + ".") }, type.name)
        }
        boundaries.forEach { rule(it).check(classes) }
    }

    private fun fixtureName(
        from: Boundary,
        to: Boundary,
    ) = "ForbiddenEdge" + from.id.replaceFirstChar(Char::uppercase) + to.id.replaceFirstChar(Char::uppercase)

    private fun writeFixtures(root: java.nio.file.Path) =
        boundaries.flatMap { from ->
            forbidden(from).map { to ->
                val name = fixtureName(from, to)
                root.resolve(name + ".java").also { file ->
                    val source =
                        "package " + from.pkg + "; public class " + name +
                            " { public " + to.example + " dependency; }"
                    Files.writeString(file, source)
                }
            }
        }

    private fun compileFixtures(root: java.nio.file.Path) {
        val files = writeFixtures(root)
        val classpath =
            (boundaries.map { Class.forName(it.example) } + Unit::class.java)
                .map {
                    java.io
                        .File(
                            it.protectionDomain.codeSource.location
                                .toURI(),
                        ).path
                }.distinct()
                .joinToString(java.io.File.pathSeparator)
        val diagnostics = StringWriter()
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) { "Architecture rejection controls require a JDK" }
        compiler.getStandardFileManager(null, null, null).use { manager ->
            val units = manager.getJavaFileObjectsFromFiles(files.map { it.toFile() })
            val arguments = listOf("-classpath", classpath, "-d", root.toString())
            val task = compiler.getTask(diagnostics, manager, null, arguments, null, units)
            assertTrue(task.call(), diagnostics.toString())
        }
    }

    @Test
    fun everyForbiddenDirectionRejectsRealCompiledBytecode() {
        val root = Files.createTempDirectory("koral-architecture-")
        try {
            compileFixtures(root)
            val fixtures = ClassFileImporter().importPath(root)
            boundaries.forEach { from ->
                val report = rule(from).evaluate(fixtures).failureReport.toString()
                forbidden(from).forEach { to ->
                    assertTrue(report.contains(fixtureName(from, to)), "Missing rejection for " + from.id + " -> " + to.id)
                }
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
