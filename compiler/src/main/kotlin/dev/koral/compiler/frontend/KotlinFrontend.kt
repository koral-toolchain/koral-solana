package dev.koral.compiler.frontend

import dev.koral.compiler.model.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

object KotlinFrontend {
    fun compile(source: File, apiSource: String): CounterProgram {
        require(source.isFile && source.extension=="kt") { "Source does not exist: $source" }
        val temporary=Files.createTempDirectory("koral-kotlin-").toFile()
        try {
            val api=File(temporary,"Api.kt").apply { writeText(apiSource) }
            val model=File(temporary,"program.ir")
            val classpath=System.getProperty("java.class.path")
            val stdlib=classpath.split(File.pathSeparator).single { File(it).name.matches(Regex("kotlin-stdlib-[0-9].*\\.jar")) }
            val plugin=File(KoralRegistrar::class.java.protectionDomain.codeSource.location.toURI())
            require(plugin.isFile) { "Use the installed CLI distribution; compiler plugin must be a JAR" }
            val command=listOf(File(System.getProperty("java.home"),"bin/java").path,"-Xmx768m","-cp",classpath,"org.jetbrains.kotlin.cli.jvm.K2JVMCompiler","-no-stdlib","-no-reflect","-classpath",stdlib,"-jvm-target","17","-Xplugin=${plugin.path}","-d",File(temporary,"jvm").path,api.path,source.canonicalPath)
            val output=File(temporary,"diagnostics.txt")
            val builder=ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output)
            builder.environment().putAll(mapOf("KORAL_SOURCE" to source.canonicalPath,"KORAL_API" to api.canonicalPath,"KORAL_IR_OUT" to model.path))
            val process=builder.start()
            if(!process.waitFor(90,TimeUnit.SECONDS)) { process.destroyForcibly();error("Kotlin compilation timed out") }
            check(process.exitValue()==0 && model.isFile) { output.readText().take(16000) }
            return ProgramCodec.read(model.toPath())
        } finally { temporary.deleteRecursively() }
    }
}
