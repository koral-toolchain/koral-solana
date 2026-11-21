package dev.koral.cli

import dev.koral.pipeline.CompilerPipeline
import java.io.File

internal fun runBuild(
    command: String,
    options: Options,
) {
    val project = if (options.positional == null) KoralProject.load() else null
    val source = options.positional?.let { File(it).canonicalFile } ?: requireNotNull(project).source
    if (command == "check") {
        val checked = CompilerPipeline.check(source)
        println("Checked ${source.name}: ${checked.instructionCount} ${checked.accountName} instruction(s)")
        return
    }
    val out = File(options.values["--out"] ?: project?.output?.path ?: "build/koral").canonicalFile
    require(out != source.parentFile) { "Use a separate output directory" }
    out.mkdirs()
    // VM reports belong to the CLI workflow; the pipeline owns compilation artifacts.
    ProjectTemplate.entries.map { File(out, it.reportFile) }.forEach {
        check(!it.exists() || it.delete()) { "Cannot invalidate $it" }
    }
    val result = CompilerPipeline.build(source, out, options.values["--sdk"]?.let(::File))
    println("Built ${result.binary.path} (${result.binary.length()} bytes)")
}
