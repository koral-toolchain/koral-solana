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
            println("Koral 0.3.0-preview.1\n  koral init <new-directory> [--template counter|payments|sol-vault|custom]\n  koral doctor [--sdk <SDK>]\n  koral setup\n  koral check [source.kt]\n  koral build [source.kt] [--out <directory>] [--sdk <SDK>]\n  koral test [counter.so] [--suite counter|payments|sol-vault|custom]\nInside an initialized project, source/output paths come from koral.properties.\nsetup explicitly installs pinned Node VM tools into your user cache.\nTargets: owned ULong state, SOL vaults or Counter; test runs a fixed template suite or your custom project script. No deployment.")
            return
        }
        val options=Options.parse(args.drop(1),when(args[0]) { "build" -> setOf("--out","--sdk");"doctor" -> setOf("--sdk");"init" -> setOf("--template");"test" -> setOf("--suite");else -> emptySet() })
        fun toolchain()=SbfToolchain(File(options.values["--sdk"] ?: System.getenv("KORAL_SBF_SDK") ?: error("Set KORAL_SBF_SDK or pass --sdk. It must point to the SDK directory containing c/sbf.ld.")))
        when(args[0]) {
            "init" -> {
                val template=ProjectTemplate.parse(options.values["--template"] ?: "counter")
                val project=KoralProject.create(File(options.positional ?: error("Usage: koral init <new-directory> [--template counter|payments|sol-vault|custom]")),template)
                println("Created ${template.id} project: ${project.root}\nOpen that directory, then run koral doctor, koral setup, koral build and koral test.\nSet KORAL_SBF_SDK to your installed Agave SBF SDK. No wallet or blockchain deployment is needed.")
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
                (listOf("counter.so","counter.build.json","counter.abi.json") + ProjectTemplate.entries.map { it.reportFile }).forEach { check(!File(out,it).exists() || File(out,it).delete()) { "Cannot invalidate $it" } }
                val program=KotlinFrontend.compile(source,Language.source())
                val sdk=toolchain()
                val runtime=resource("src/counter.c")
                val c=File(out,"counter.c").apply { writeText(CounterC().compile(program,runtime)) }
                ProgramCodec.write(program,File(out,"counter.ir").toPath())
                val include=File(out,"include").apply { mkdirs() }
                File(include,"koral.h").writeText(resource("include/koral.h"))
                val binary=File(out,"counter.so")
                sdk.build(c,include,binary)
                val manifest=linkedMapOf("compiler" to "koral-0.3.0-preview.1","kotlin" to "2.2.21","target" to "sbpf-v0","scope" to "Owned ULong state, external references, authority System transfers and rent-preserving owned-state SOL vault; own C backend", "compilerJarSha256" to hash(File(CounterC::class.java.protectionDomain.codeSource.location.toURI()).readBytes()),"sourceSha256" to hash(source.readBytes()),"runtimeSha256" to hash(runtime.toByteArray()),"generatedCSha256" to hash(c.readBytes()),"binarySha256" to hash(binary.readBytes()),"binaryBytes" to binary.length().toString(),"accountDataBytes" to (41+8*(program.account?.fields?.size ?: 1)).toString(),"accountType" to (program.account?.name ?: "Counter"),"stateFields" to (program.account?.fields?.joinToString(",") ?: "count"),"pdaSeed" to (program.account?.seed ?: "counter"),"clang" to sdk.version())
                File(out,"counter.build.json").writeText(manifest.entries.joinToString(",\n","{\n","\n}\n") { "  ${quote(it.key)}: ${quote(it.value)}" })
                File(out,"counter.abi.json").writeText(ProgramAbi.encode(program))
                println("Built ${binary.path} (${binary.length()} bytes)")
            }
            "test" -> {
                val project=if(options.positional==null) KoralProject.load() else null
                val template=options.values["--suite"]?.let(ProjectTemplate::parse) ?: project?.template ?: ProjectTemplate.COUNTER
                val binary=options.positional?.let { File(it).canonicalFile } ?: File(requireNotNull(project).output,"counter.so")
                val customScript=if(template==ProjectTemplate.CUSTOM) {
                    require(project!=null) { "Custom tests require an initialized project; run koral test inside it" }
                    File(project.root,"tests/program.test.mjs").canonicalFile.also {
                        require(it.toPath().startsWith(project.root.toPath())) { "Custom test script must stay inside the project" }
                    }
                } else null
                val report=System.getenv("KORAL_TEST_RESULT")?.takeIf { it.isNotBlank() }?.let { File(it).canonicalFile }
                    ?: if(project!=null) File(binary.parentFile,template.reportFile) else null
                require(report==null || report.canonicalFile !in listOf(binary.canonicalFile, File(binary.parentFile,"counter.abi.json").canonicalFile, File(binary.parentFile,"counter.build.json").canonicalFile, project?.source, customScript)) { "Test result must not overwrite the binary, ABI, build manifest, project source or test script" }
                // No prior passing result may survive a missing build/dependency or a failed VM run.
                require(report==null || !report.exists() || report.isFile) { "Test result must be a file, not a directory" }
                if(report!=null && report.exists()) check(report.delete()) { "Cannot invalidate old test result at $report" }
                require(binary.isFile) { "No built program at $binary. Run koral build first, or pass a .so file." }
                if(template!=ProjectTemplate.COUNTER) require(File(binary.parentFile,"counter.abi.json").isFile) {
                    "Missing counter.abi.json beside $binary. Run koral build first; ${template.id} verification requires its generated ABI."
                }
                require(report==null || report.parentFile.isDirectory) { "Test result directory does not exist: ${report?.parentFile}" }
                require(customScript==null || customScript.isFile) { "Missing tests/program.test.mjs; restore your custom test script or create a new custom project" }
                val runner=TestTools(distributionHome()).runner(template)
                val wrapper=File(runner.parentFile,"suite.mjs")
                require(wrapper.isFile) { "Missing suite dispatcher; reinstall the CLI and run koral setup" }
                val command=mutableListOf("node",wrapper.path,template.id,binary.path)
                if(report!=null) command.add(report.path)
                if(customScript!=null) command.add(customScript.path)
                val builder=ProcessBuilder(command).inheritIO()
                if(report!=null) builder.environment()["KORAL_TEST_RESULT"]=report.path
                try {
                    val p=builder.start()
                    if(!p.waitFor(90,java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly().waitFor(5,java.util.concurrent.TimeUnit.SECONDS);error("Local ${template.id} verification timed out") }
                    check(p.exitValue()==0) { if(template==ProjectTemplate.CUSTOM) "Custom test script failed. Review your test script and the source locations above. Run koral setup if VM tools are missing." else "${template.id} verification failed. This suite expects the ${template.id} template contract; review your source and generated ABI. Run koral setup if VM dependencies are missing." }
                    if(report!=null) check(report.isFile) { "${template.id} verification did not produce its result report" }
                    println(if(template==ProjectTemplate.CUSTOM) "Custom test script completed successfully. Assertions and coverage are defined by your script." else "${template.id} verified in a local VM. No blockchain transaction was sent.")
                } catch(e:Exception) {
                    report?.delete()
                    throw e
                }
            }
            else -> error("Unknown command ${args[0]}")
        }
    } catch(e:Exception) { System.err.println("Koral: ${e.message}");exitProcess(1) }
}
private object MainMarker
