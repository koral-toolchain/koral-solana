package dev.koral.cli
import dev.koral.toolchain.SbfToolchain
import java.io.File

internal fun Options.toolchain() =
    SbfToolchain(
        File(
            values["--sdk"] ?: System.getenv("KORAL_SBF_SDK")
                ?: error("Set KORAL_SBF_SDK or pass --sdk. It must point to the SDK directory containing c/sbf.ld."),
        ),
    )
