package dev.koral.cli

import java.io.File

internal fun runDoctor(options: Options) {
    require(options.positional == null) { "Usage: koral doctor [--sdk <SDK>]" }
    println("Java ${System.getProperty("java.version")} (compiler host)")
    val sdk = runCatching { options.toolchain().version() }
    sdk.onSuccess { println("SDK ready: $it") }.onFailure {
        println(
            (
                "SDK not ready: ${it.message}\nInstall Agave 3.1.10 with platform-tools " +
                    "1.52. The SDK must contain c/inc, c/sbf.ld and dependencies/platform-tools.\nExport " +
                    "KORAL_SBF_SDK=/path/to/solana-release/bin/platform-tools-sdk/sbf, or pass " +
                    "--sdk <SDK>.\nSetup guide: https://github.com/koral-toolchain/koral-solana/blob/main/docs/guides/quickstart.md"
            ),
        )
    }
    val node = capture(listOf("node", "--version"))
    println(
        if (supportedNode(
                node,
            )
        ) {
            "Node $node (local VM tests only)"
        } else {
            "Node 22.12+ not available; needed only for local VM tests, not compilation"
        },
    )
    val tools = TestTools(distributionHome())
    println(
        if (supportedNode(node) &&
            tools.healthy()
        ) {
            "VM dependencies ready"
        } else {
            "VM dependencies not ready. Install Node with npm, then run koral setup (network required)."
        },
    )
    check(sdk.isSuccess) { "Compiler SDK is not ready; follow the instructions above. doctor did not install anything." }
}
