package dev.koral.cli

import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        if (args.isEmpty() || args[0] in listOf("help", "--help")) {
            println(
                (
                    "Koral 0.3.0-preview.1\n  koral init <new-directory> [--template counter|payments|sol-vault|custom]\n " +
                        " koral doctor [--sdk <SDK>]\n  koral setup\n  koral check [source.kt]\n " +
                        " koral build [source.kt] [--out <directory>] [--sdk <SDK>]\n  koral test " +
                        "[counter.so] [--suite counter|payments|sol-vault|custom]\nInside an initialized " +
                        "project, source/output paths come from koral.properties.\nsetup explicitly " +
                        "installs pinned Node VM tools into your user cache.\nTargets: owned ULong " +
                        "state, SOL vaults or Counter; test runs a fixed template suite or your " +
                        "custom project script. No deployment."
                ),
            )
            return
        }
        val command = args[0]
        val options = Options.parse(args.drop(1), allowedOptions(command))
        dispatch(command, options)
    } catch (e: Exception) {
        System.err.println("Koral: ${e.message}")
        exitProcess(1)
    }
}

internal fun distributionHome() =
    File(
        System.getProperty(
            "koral.home",
        ) ?: File(
            MainMarker::class.java.protectionDomain.codeSource.location
                .toURI(),
        ).parentFile.parentFile.path,
    )

private object MainMarker

private fun dispatch(
    command: String,
    options: Options,
) {
    when (command) {
        "init" -> runInit(options)
        "setup" -> {
            require(options.positional == null) { "Usage: koral setup" }
            TestTools(distributionHome()).setup()
        }
        "doctor" -> runDoctor(options)
        "check", "build" -> runBuild(command, options)
        "test" -> runTest(options)
        else -> error("Unknown command $command")
    }
}

private fun allowedOptions(command: String): Set<String> =
    when (command) {
        "build" -> setOf("--out", "--sdk")
        "doctor" -> setOf("--sdk")
        "init" -> setOf("--template")
        "test" -> setOf("--suite")
        else -> emptySet()
    }
