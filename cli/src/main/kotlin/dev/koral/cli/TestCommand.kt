package dev.koral.cli

import dev.koral.api.Language
import dev.koral.compiler.backend.c.CounterC
import dev.koral.compiler.frontend.KotlinFrontend
import dev.koral.compiler.metadata.ProgramAbi
import dev.koral.compiler.model.ProgramCodec
import dev.koral.toolchain.SbfToolchain
import java.io.File
import java.security.MessageDigest

internal fun runTest(options: Options) {
    val project = if (options.positional == null) KoralProject.load() else null
    val template = options.values["--suite"]?.let(ProjectTemplate::parse) ?: project?.template ?: ProjectTemplate.COUNTER
    val binary = options.positional?.let { File(it).canonicalFile } ?: File(requireNotNull(project).output, "counter.so")
    val customScript =
        if (template == ProjectTemplate.CUSTOM) {
            require(project != null) { "Custom tests require an initialized project; run koral test inside it" }
            File(project.root, "tests/program.test.mjs").canonicalFile.also {
                require(it.toPath().startsWith(project.root.toPath())) { "Custom test script must stay inside the project" }
            }
        } else {
            null
        }
    val report =
        System.getenv("KORAL_TEST_RESULT")?.takeIf { it.isNotBlank() }?.let { File(it).canonicalFile }
            ?: if (project != null) File(binary.parentFile, template.reportFile) else null
    validateTestInputs(template, binary, customScript, report, project)
    executeTest(template, binary, customScript, report)
}

private fun executeTest(
    template: ProjectTemplate,
    binary: File,
    customScript: File?,
    report: File?,
) {
    val runner = TestTools(distributionHome()).runner(template)
    val wrapper = File(runner.parentFile, "suite.mjs")
    require(wrapper.isFile) { "Missing suite dispatcher; reinstall the CLI and run koral setup" }
    val command = mutableListOf("node", wrapper.path, template.id, binary.path)
    if (report != null) command.add(report.path)
    if (customScript != null) command.add(customScript.path)
    val builder = ProcessBuilder(command).inheritIO()
    if (report != null) builder.environment()["KORAL_TEST_RESULT"] = report.path
    try {
        val p = builder.start()
        if (!p.waitFor(90, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly().waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            error("Local ${template.id} verification timed out")
        }
        check(p.exitValue() == 0) {
            if (template ==
                ProjectTemplate.CUSTOM
            ) {
                (
                    "Custom test script failed. Review your test script and the source locations " +
                        "above. Run koral setup if VM tools are missing."
                )
            } else {
                (
                    "${template.id} verification failed. This suite expects the ${template.id} " +
                        "template contract; review your source and generated ABI. Run koral setup " +
                        "if VM dependencies are missing."
                )
            }
        }
        if (report != null) check(report.isFile) { "${template.id} verification did not produce its result report" }
        println(
            if (template ==
                ProjectTemplate.CUSTOM
            ) {
                "Custom test script completed successfully. Assertions and coverage are defined by your script."
            } else {
                "${template.id} verified in a local VM. No blockchain transaction was sent."
            },
        )
    } catch (e: Exception) {
        report?.delete()
        throw e
    }
}

private fun validateTestInputs(
    template: ProjectTemplate,
    binary: File,
    customScript: File?,
    report: File?,
    project: KoralProject?,
) {
    require(
        report == null ||
            report.canonicalFile !in
            listOf(
                binary.canonicalFile,
                File(binary.parentFile, "counter.abi.json").canonicalFile,
                File(binary.parentFile, "counter.build.json").canonicalFile,
                project?.source,
                customScript,
            ),
    ) { "Test result must not overwrite the binary, ABI, build manifest, project source or test script" }
    // No prior passing result may survive a missing build/dependency or a failed VM run.
    require(report == null || !report.exists() || report.isFile) { "Test result must be a file, not a directory" }
    if (report != null && report.exists()) check(report.delete()) { "Cannot invalidate old test result at $report" }
    require(binary.isFile) { "No built program at $binary. Run koral build first, or pass a .so file." }
    if (template != ProjectTemplate.COUNTER) {
        require(File(binary.parentFile, "counter.abi.json").isFile) {
            (
                "Missing counter.abi.json beside $binary. Run koral build first; ${template.id} " +
                    "verification requires its generated ABI."
            )
        }
    }
    require(report == null || report.parentFile.isDirectory) { "Test result directory does not exist: ${report?.parentFile}" }
    require(customScript == null || customScript.isFile) {
        "Missing tests/program.test.mjs; restore your custom test script or create a new custom project"
    }
}
