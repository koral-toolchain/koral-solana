package dev.koral.cli

import dev.koral.api.Language
import dev.koral.compiler.backend.c.CounterC
import dev.koral.compiler.frontend.KotlinFrontend
import dev.koral.compiler.metadata.ProgramAbi
import dev.koral.compiler.model.ProgramCodec
import dev.koral.toolchain.SbfToolchain
import java.io.File
import java.security.MessageDigest

internal fun runInit(options: Options) {
    val template = ProjectTemplate.parse(options.values["--template"] ?: "counter")
    val project =
        KoralProject.create(
            File(options.positional ?: error("Usage: koral init <new-directory> [--template counter|payments|sol-vault|custom]")),
            template,
        )
    println(
        (
            "Created ${template.id} project: ${project.root}\nOpen that directory, " +
                "then run koral doctor, koral setup, koral build and koral test.\nSet KORAL_SBF_SDK " +
                "to your installed Agave SBF SDK. No wallet or blockchain deployment is " +
                "needed."
        ),
    )
}
