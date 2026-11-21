package dev.koral.cli

import dev.koral.api.Language
import dev.koral.compiler.backend.c.CounterC
import dev.koral.compiler.frontend.KotlinFrontend
import dev.koral.compiler.metadata.ProgramAbi
import dev.koral.compiler.model.ProgramCodec
import dev.koral.toolchain.SbfToolchain
import java.io.File
import java.security.MessageDigest

private fun resource(path: String) =
    requireNotNull(CounterC::class.java.getResourceAsStream("/koral/runtime/$path")) {
        "Missing runtime $path"
    }.bufferedReader().use { it.readText() }

private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun quote(s: String) =
    "\"" +
        s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""

internal fun runBuild(
    command: String,
    options: Options,
) {
    val project = if (options.positional == null)KoralProject.load() else null
    val source = options.positional?.let { File(it).canonicalFile } ?: requireNotNull(project).source
    if (command == "check") {
        val checked = KotlinFrontend.compile(source, Language.source())
        println(
            (
                "Checked ${source.name}: ${checked.instructions.size} ${checked.account?.name ?: "Counter"} " +
                    "instruction(s)"
            ),
        )
        return
    }
    val out = File(options.values["--out"] ?: project?.output?.path ?: "build/koral").canonicalFile
    require(out != source.parentFile) { "Use a separate output directory" }
    out.mkdirs()
    // Remove stale success artifacts even when the next compilation fails.
    (listOf("counter.so", "counter.build.json", "counter.abi.json") + ProjectTemplate.entries.map { it.reportFile }).forEach {
        check(!File(out, it).exists() || File(out, it).delete()) { "Cannot invalidate $it" }
    }
    val program = KotlinFrontend.compile(source, Language.source())
    val sdk = options.toolchain()
    val runtime = resource("src/counter.c")
    val c = File(out, "counter.c").apply { writeText(CounterC().compile(program, runtime)) }
    ProgramCodec.write(program, File(out, "counter.ir").toPath())
    val include = File(out, "include").apply { mkdirs() }
    File(include, "koral.h").writeText(resource("include/koral.h"))
    val binary = File(out, "counter.so")
    sdk.build(c, include, binary)
    writeManifest(program, source, runtime, binary, sdk)
    File(out, "counter.abi.json").writeText(ProgramAbi.encode(program))
    println("Built ${binary.path} (${binary.length()} bytes)")
}

private fun writeManifest(
    program: dev.koral.compiler.model.CounterProgram,
    source: File,
    runtime: String,
    binary: File,
    sdk: SbfToolchain,
) {
    val manifest =
        linkedMapOf(
            "compiler" to "koral-0.3.0-preview.1",
            "kotlin" to "2.2.21",
            "target" to "sbpf-v0",
            "scope" to
                (
                    "Owned ULong state, external references, authority System transfers and " +
                        "rent-preserving owned-state SOL vault; own C backend"
                ),
            "compilerJarSha256" to
                hash(
                    File(
                        CounterC::class.java.protectionDomain.codeSource.location
                            .toURI(),
                    ).readBytes(),
                ),
            "sourceSha256" to hash(source.readBytes()),
            "runtimeSha256" to hash(runtime.toByteArray()),
            "generatedCSha256" to hash(File(binary.parentFile, "counter.c").readBytes()),
            "binarySha256" to hash(binary.readBytes()),
            "binaryBytes" to binary.length().toString(),
            "accountDataBytes" to (41 + 8 * (program.account?.fields?.size ?: 1)).toString(),
            "accountType" to (program.account?.name ?: "Counter"),
            "stateFields" to (program.account?.fields?.joinToString(",") ?: "count"),
            "pdaSeed" to (program.account?.seed ?: "counter"),
            "clang" to sdk.version(),
        )
    File(
        binary.parentFile,
        "counter.build.json",
    ).writeText(manifest.entries.joinToString(",\n", "{\n", "\n}\n") { "  ${quote(it.key)}: ${quote(it.value)}" })
}
