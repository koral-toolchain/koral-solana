package dev.koral.pipeline

import dev.koral.api.Language
import dev.koral.compiler.abi.ProgramAbi
import dev.koral.compiler.ir.CounterProgram
import dev.koral.compiler.ir.ProgramCodec
import dev.koral.compiler.kotlin.KotlinCompiler
import dev.koral.compiler.target.c.CounterC
import dev.koral.toolchain.SbfToolchain
import java.io.File
import java.security.MessageDigest

/** Build entry point shared by JVM callers and the CLI; no user JVM code is executed. */
object CompilerPipeline {
    fun check(source: File): ProgramSummary = summary(analyze(source))

    private fun analyze(source: File): CounterProgram = KotlinCompiler.compile(source, Language.source())

    private fun summary(program: CounterProgram) = ProgramSummary(program.instructions.size, program.account?.name ?: "Counter")

    fun build(
        source: File,
        output: File,
        sdk: File? = null,
    ): CompilationResult {
        val input = source.canonicalFile
        val out = output.canonicalFile
        require(out != input.parentFile) { "Use a separate output directory" }
        out.mkdirs()
        val success = listOf("counter.so", "counter.build.json", "counter.abi.json").map { File(out, it) }
        success.forEach { kotlin.check(!it.exists() || it.delete()) { "Cannot invalidate $it" } }
        try {
            val program = analyze(input)
            val toolchain =
                SbfToolchain(
                    sdk ?: File(
                        System.getenv("KORAL_SBF_SDK")
                            ?: error("Set KORAL_SBF_SDK or pass --sdk. It must point to the SDK directory containing c/sbf.ld."),
                    ),
                )
            val runtime = resource("src/counter.c")
            val c = File(out, "counter.c").apply { writeText(CounterC().compile(program, runtime)) }
            ProgramCodec.write(program, File(out, "counter.ir").toPath())
            val include = File(out, "include").apply { mkdirs() }
            File(include, "koral.h").writeText(resource("include/koral.h"))
            val binary = File(out, "counter.so")
            toolchain.build(c, include, binary)
            writeManifest(program, input, runtime, binary, toolchain)
            val abi = File(out, "counter.abi.json").apply { writeText(ProgramAbi.encode(program)) }
            return CompilationResult(summary(program), binary, abi, File(out, "counter.build.json"))
        } catch (failure: Exception) {
            success.forEach { it.delete() }
            throw failure
        }
    }
}

data class ProgramSummary(
    val instructionCount: Int,
    val accountName: String,
)

data class CompilationResult(
    val summary: ProgramSummary,
    val binary: File,
    val abi: File,
    val manifest: File,
)

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

private fun writeManifest(
    program: dev.koral.compiler.ir.CounterProgram,
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
