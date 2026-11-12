package dev.koral.toolchain

import java.io.File
import java.util.concurrent.TimeUnit

class SbfToolchain(val sdk: File) {
    private val tools=File(sdk,"dependencies/platform-tools")
    private val llvm=File(tools,"llvm")
    private val clang=File(llvm,"bin/clang")
    init { require(clang.isFile && File(sdk,"c/sbf.ld").isFile) { "Invalid SDK; set KORAL_SBF_SDK to an Agave SBF SDK directory" } }
    private fun run(args: List<String>): String {
        val output=File.createTempFile("koral-tool-",".log")
        try {
            val process=ProcessBuilder(args).redirectErrorStream(true).redirectOutput(output).start()
            if(!process.waitFor(90,TimeUnit.SECONDS)) { process.destroyForcibly();error("Tool timed out: ${args[0]}") }
            check(process.exitValue()==0) { output.readText().take(16000) }
            return output.readText().trim()
        } finally { output.delete() }
    }
    fun version(): String = run(listOf(clang.path,"--version")).lineSequence().first()
    fun build(c: File, include: File, binary: File) {
        val resource=File(llvm,"lib/clang").listFiles()?.filter { File(it,"include").isDirectory }?.singleOrNull() ?: error("Expected one Clang resource directory")
        val includes=listOf(File(sdk,"c/inc"),File(resource,"include"),File(llvm,"sbpf/include"),include)
        val obj=File(binary.parentFile,"counter.o")
        val builtins=File(tools,"rust/lib/rustlib/sbpf-solana-solana/lib").listFiles()?.singleOrNull { it.name.startsWith("libcompiler_builtins-") && it.extension=="rlib" } ?: error("Missing compiler builtins")
        try {
            run(listOf(clang.path,"-O2","-Werror","-Wframe-larger-than=4096","-fno-builtin","-ffunction-sections","-fdata-sections","-std=c17","-target","sbf","-fPIC")+includes.flatMap { listOf("-isystem",it.path) }+listOf("-c",c.path,"-o",obj.path))
            run(listOf(File(llvm,"bin/ld.lld").path,"-z","notext","-shared","--Bdynamic",File(sdk,"c/sbf.ld").path,"--entry","entrypoint","--gc-sections","--strip-all","-L",File(llvm,"lib/sbpf").path,"-lc",builtins.path,obj.path,"-o",binary.path))
        } finally { obj.delete() }
    }
}
