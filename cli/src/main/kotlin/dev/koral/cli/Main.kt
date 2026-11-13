package dev.koral.cli

import dev.koral.api.Language
import dev.koral.compiler.frontend.KotlinFrontend
import dev.koral.compiler.backend.c.CounterC
import dev.koral.compiler.model.ProgramCodec
import dev.koral.compiler.metadata.ProgramAbi
import dev.koral.toolchain.SbfToolchain
import java.io.File
import java.security.MessageDigest
import kotlin.system.exitProcess

private fun resource(path:String)=requireNotNull(CounterC::class.java.getResourceAsStream("/koral/runtime/$path")) { "Missing runtime $path" }.bufferedReader().use { it.readText() }
private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
private fun quote(s:String)="\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\""
private fun distributionHome()=File(System.getProperty("koral.home") ?: File(MainMarker::class.java.protectionDomain.codeSource.location.toURI()).parentFile.parentFile.path)
fun main(args:Array<String>) {
    try {
        if(args.isEmpty() || args[0] in listOf("help","--help")) {
            println("Koral 0.1.0-dev\n  koral init <new-directory>\n  koral doctor [--sdk <SDK>]\n  koral setup\n  koral check [source.kt]\n  koral build [source.kt] [--out <directory>] [--sdk <SDK>]\n  koral test [counter.so]\nInside an initialized project, source/output paths come from koral.properties.\nsetup explicitly installs pinned Node VM tools into your user cache.\nTargets: owned ULong state, SOL vaults or Counter; test is Counter-only. No deployment.")
            return
        }
        val options=Options.parse(args.drop(1),when(args[0]) { "build" -> setOf("--out","--sdk");"doctor" -> setOf("--sdk");else -> emptySet() })
        fun toolchain()=SbfToolchain(File(options.values["--sdk"] ?: System.getenv("KORAL_SBF_SDK") ?: error("Set KORAL_SBF_SDK or pass --sdk. It must point to the SDK directory containing c/sbf.ld.")))
        when(args[0]) {
            "init" -> {
                val project=KoralProject.create(File(options.positional ?: error("Usage: koral init <new-directory>")))
                println("Created Counter project: ${project.root}\nOpen that directory, then run koral doctor, koral setup, koral build and koral test.\nSet KORAL_SBF_SDK to your installed Agave SBF SDK. No wallet or blockchain deployment is needed.")
            }
            "setup" -> {
                require(options.positional==null) { "Usage: koral setup" }
                TestTools(distributionHome()).setup()
            }
            "doctor" -> {
                require(options.positional==null) { "Usage: koral doctor [--sdk <SDK>]" }
                println("Java ${System.getProperty("java.version")} (compiler host)")
                val sdk=runCatching { toolchain().version() }
                sdk.onSuccess { println("SDK ready: $it") }.onFailure {
                    println("SDK not ready: ${it.message}\nInstall Agave 3.1.10 with platform-tools 1.52. The SDK must contain c/inc, c/sbf.ld and dependencies/platform-tools.\nExport KORAL_SBF_SDK=/path/to/solana-release/bin/platform-tools-sdk/sbf, or pass --sdk <SDK>.\nSetup guide: https://github.com/koral-toolchain/koral-solana/blob/main/docs/guides/quickstart.md")
                }
                val node=capture(listOf("node","--version"))
                println(if(supportedNode(node))"Node $node (local VM tests only)" else "Node 22.12+ not available; needed only for local VM tests, not compilation")
                val tools=TestTools(distributionHome())
                println(if(supportedNode(node) && tools.healthy())"VM dependencies ready" else "VM dependencies not ready. Install Node with npm, then run koral setup (network required).")
                check(sdk.isSuccess) { "Compiler SDK is not ready; follow the instructions above. doctor did not install anything." }
            }
            "check","build" -> {
                val project=if(options.positional==null)KoralProject.load() else null
                val source=options.positional?.let { File(it).canonicalFile } ?: requireNotNull(project).source
                if(args[0]=="check") {
                    val checked=KotlinFrontend.compile(source,Language.source())
                    println("Checked ${source.name}: ${checked.instructions.size} ${checked.account?.name ?: "Counter"} instruction(s)")
                    return
                }
                val out=File(options.values["--out"] ?: project?.output?.path ?: "build/koral").canonicalFile
                require(out!=source.parentFile) { "Use a separate output directory" }
                out.mkdirs()
                // Remove stale success artifacts even when the next compilation fails.
                listOf("counter.so","counter.build.json","counter.abi.json").forEach { check(!File(out,it).exists() || File(out,it).delete()) { "Cannot invalidate $it" } }
                val program=KotlinFrontend.compile(source,Language.source())
                val sdk=toolchain()
                val runtime=resource("src/counter.c")
                val c=File(out,"counter.c").apply { writeText(CounterC().compile(program,runtime)) }
                ProgramCodec.write(program,File(out,"counter.ir").toPath())
                val include=File(out,"include").apply { mkdirs() }
                File(include,"koral.h").writeText(resource("include/koral.h"))
                val binary=File(out,"counter.so")
                sdk.build(c,include,binary)
                val manifest=linkedMapOf("compiler" to "koral-0.1.0-dev","kotlin" to "2.2.21","target" to "sbpf-v0","scope" to "Owned ULong state, external references, authority System transfers and rent-preserving owned-state SOL vault; own C backend", "compilerJarSha256" to hash(File(CounterC::class.java.protectionDomain.codeSource.location.toURI()).readBytes()),"sourceSha256" to hash(source.readBytes()),"runtimeSha256" to hash(runtime.toByteArray()),"generatedCSha256" to hash(c.readBytes()),"binarySha256" to hash(binary.readBytes()),"binaryBytes" to binary.length().toString(),"accountDataBytes" to (41+8*(program.account?.fields?.size ?: 1)).toString(),"accountType" to (program.account?.name ?: "Counter"),"stateFields" to (program.account?.fields?.joinToString(",") ?: "count"),"pdaSeed" to (program.account?.seed ?: "counter"),"clang" to sdk.version())
                File(out,"counter.build.json").writeText(manifest.entries.joinToString(",\n","{\n","\n}\n") { "  ${quote(it.key)}: ${quote(it.value)}" })
                File(out,"counter.abi.json").writeText(ProgramAbi.encode(program))
                println("Built ${binary.path} (${binary.length()} bytes)")
            }
            "test" -> {
                val binary=options.positional?.let { File(it).canonicalFile } ?: File(KoralProject.load().output,"counter.so")
                require(binary.isFile) { "No built Counter at $binary. Run koral build first, or pass a Counter .so file." }
                val runner=TestTools(distributionHome()).runner()
                val builder=ProcessBuilder("node",runner.path,binary.path).inheritIO()
                if(options.positional==null && builder.environment()["KORAL_TEST_RESULT"]==null) builder.environment()["KORAL_TEST_RESULT"]=File(binary.parentFile,"counter.test.json").path
                val p=builder.start()
                if(!p.waitFor(90,java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly();error("Local Counter verification timed out") }
                check(p.exitValue()==0) { "Counter verification failed. This test command only supports Counter; check the source and run koral setup to repair VM dependencies." }
                println("Counter verified in a local VM. No blockchain transaction was sent.")
            }
            else -> error("Unknown command ${args[0]}")
        }
    } catch(e:Exception) { System.err.println("Koral: ${e.message}");exitProcess(1) }
}
private object MainMarker
