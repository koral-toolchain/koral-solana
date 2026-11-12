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
fun main(args:Array<String>) {
    try {
        if(args.isEmpty() || args[0] in listOf("help","--help")) {
            println("Koral 0.1.0-dev\n  koral check <source.kt>\n  koral build <source.kt> [--out <directory>] [--sdk <SDK>]\n  koral doctor [--sdk <SDK>]\n  koral test <counter.so>\nTargets: owned ULong state or Counter; test is Counter-only. No deployment.")
            return
        }
        fun option(name:String):String? { val index=args.indexOf(name);return if(index<0)null else args.getOrNull(index+1) ?: error("Missing $name value") }
        fun toolchain()=SbfToolchain(File(option("--sdk") ?: System.getenv("KORAL_SBF_SDK") ?: error("Set KORAL_SBF_SDK or pass --sdk")))
        when(args[0]) {
            "doctor" -> { println("Java ${System.getProperty("java.version")}");println(toolchain().version()) }
            "check","build" -> {
                val source=File(args.getOrNull(1) ?: error("Missing source")).canonicalFile
                if(args[0]=="check") {
                    require(args.size==2) { "Usage: koral check <source.kt>" }
                    val checked=KotlinFrontend.compile(source,Language.source())
                    println("Checked ${source.name}: ${checked.instructions.size} ${checked.account?.name ?: "Counter"} instruction(s)")
                    return
                }
                val extras=args.drop(2)
                require(extras.size%2==0 && extras.chunked(2).all { it[0] in listOf("--out","--sdk") } && extras.chunked(2).map { it[0] }.distinct().size==extras.size/2) { "Usage: koral build <source.kt> [--out <directory>] [--sdk <SDK>]" }
                val out=File(option("--out") ?: "build/koral").canonicalFile
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
                val manifest=linkedMapOf("compiler" to "koral-0.1.0-dev","kotlin" to "2.2.21","target" to "sbpf-v0","scope" to "Owned ULong state, external account references and authority-funded System transfer; own C backend", "compilerJarSha256" to hash(File(CounterC::class.java.protectionDomain.codeSource.location.toURI()).readBytes()),"sourceSha256" to hash(source.readBytes()),"runtimeSha256" to hash(runtime.toByteArray()),"generatedCSha256" to hash(c.readBytes()),"binarySha256" to hash(binary.readBytes()),"binaryBytes" to binary.length().toString(),"accountDataBytes" to (41+8*(program.account?.fields?.size ?: 1)).toString(),"accountType" to (program.account?.name ?: "Counter"),"stateFields" to (program.account?.fields?.joinToString(",") ?: "count"),"pdaSeed" to (program.account?.seed ?: "counter"),"clang" to sdk.version())
                File(out,"counter.build.json").writeText(manifest.entries.joinToString(",\n","{\n","\n}\n") { "  ${quote(it.key)}: ${quote(it.value)}" })
                File(out,"counter.abi.json").writeText(ProgramAbi.encode(program))
                println("Built ${binary.path} (${binary.length()} bytes)")
            }
            "test" -> {
                val root=File(System.getProperty("koral.home") ?: File(MainMarker::class.java.protectionDomain.codeSource.location.toURI()).parentFile.parentFile.path)
                val binary=File(args.getOrNull(1) ?: error("Missing counter.so")).canonicalFile
                val runner=File(root,"testing/counter.mjs")
                require(runner.isFile) { "Missing test runner in $root" }
                val p=ProcessBuilder("node",runner.path,binary.path).inheritIO().start()
                check(p.waitFor()==0) { "Counter tests failed; install testing dependencies with npm ci first" }
            }
            else -> error("Unknown command ${args[0]}")
        }
    } catch(e:Exception) { System.err.println("Koral: ${e.message}");exitProcess(1) }
}
private object MainMarker
