package dev.koral.cli

import java.io.File
import java.nio.file.Files
import java.util.Properties

internal data class KoralProject(val root: File, val source: File, val output: File) {
    companion object {
        fun load(directory: File = File(".")): KoralProject {
            val root = directory.canonicalFile
            val config = File(root, "koral.properties")
            require(config.isFile) { "No koral.properties in $root. Run koral init <directory>, then cd into it; or pass an explicit source/binary." }
            val values = Properties().apply { config.reader(Charsets.UTF_8).use { load(it) } }
            require(values.stringPropertyNames() == setOf("format", "source", "output") && values.getProperty("format") == "1") {
                "koral.properties requires format=1, source and output; unknown settings are not supported"
            }
            fun relative(key: String): File {
                val value = values.getProperty(key)
                require(value.isNotBlank() && !File(value).isAbsolute) { "$key must be a non-empty project-relative path" }
                val file = File(root, value).canonicalFile
                require(file != root && file.toPath().startsWith(root.toPath())) { "$key must stay inside the project directory" }
                return file
            }
            val source = relative("source")
            val output = relative("output")
            require(source.extension == "kt") { "Project source must be a .kt file" }
            require(output != source && output != source.parentFile) { "Project output must be separate from the source" }
            return KoralProject(root, source, output)
        }

        fun create(directory: File): KoralProject {
            val target = directory.canonicalFile.toPath()
            require(!Files.exists(target)) { "Destination already exists: $target. Choose a new directory; existing files are never replaced." }
            require(Files.isDirectory(target.parent)) { "Parent directory does not exist: ${target.parent}" }
            Files.createDirectory(target)
            Files.createDirectory(target.resolve("src"))
            target.resolve("src/counter.kt").toFile().writeText("""
                package example
                import dev.koral.api.*

                @Account("counter")
                data class Counter(val count: ULong)

                @Instruction
                fun increment(state: Counter, by: ULong): Counter =
                    Counter(checkedAdd(state.count, by))
            """.trimIndent() + "\n")
            target.resolve("koral.properties").toFile().writeText("format=1\nsource=src/counter.kt\noutput=build/koral\n")
            target.resolve(".gitignore").toFile().writeText("/build/\n*.keypair.json\n*-keypair.json\n")
            target.resolve("README.md").toFile().writeText("""
                # First Koral Counter

                A local Kotlin-to-Solana example. No wallet or blockchain deployment is needed.

                Requirements: JDK 17+, an installed Agave 3.1.10 / platform-tools 1.52 SBF SDK,
                and Node 22.12+ with npm for local VM tests. Add the extracted Koral bin directory
                to PATH. Set KORAL_SBF_SDK to your SDK directory (the one containing c/sbf.ld).

                ```sh
                koral doctor
                koral setup
                koral check
                koral build
                koral test
                ```

                setup explicitly installs pinned VM dependencies into your user cache. It needs
                network access; build and test do not install dependencies automatically.
                KORAL_CACHE_DIR overrides the cache root. Source files remain in this project;
                setup does not write into the installed Koral distribution.

                Configuration: koral.properties selects src/counter.kt and build/koral.
                The build writes counter.so, counter.c, counter.ir, counter.abi.json and
                counter.build.json. test runs the Counter verifier against the built artifact.
                A successful native run reports 55 checks, including an actual increment,
                permissions, arithmetic boundaries and rollback. It is not a security audit.

                Change the Kotlin source, then run build and test again. Unsupported source
                fails compilation; a failed build invalidates old success artifacts.
                These commands do not connect to an RPC, deploy a program or transfer funds.
                Koral usage remains subject to the LICENSE supplied with the CLI.
            """.trimIndent() + "\n")
            return load(target.toFile())
        }
    }
}

internal data class Options(val positional: String?, val values: Map<String, String>) {
    companion object {
        fun parse(arguments: List<String>, allowed: Set<String>): Options {
            val values = linkedMapOf<String, String>()
            var positional: String? = null
            var i = 0
            while (i < arguments.size) {
                val value = arguments[i++]
                if (value.startsWith("--")) {
                    require(value in allowed) { "Unknown option $value" }
                    require(value !in values) { "Repeated option $value" }
                    val next = arguments.getOrNull(i++)
                    require(!next.isNullOrBlank() && !next.startsWith("--")) { "Missing $value value" }
                    values[value] = next
                } else {
                    require(positional == null) { "Expected at most one source, binary or project directory" }
                    positional = value
                }
            }
            return Options(positional, values)
        }
    }
}
