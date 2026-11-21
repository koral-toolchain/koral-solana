package dev.koral.cli

import java.io.File
import java.nio.file.Files
import java.util.Properties

internal enum class ProjectTemplate(
    val id: String,
    val sourceFile: String,
    val runner: String,
    val reportFile: String,
) {
    COUNTER("counter", "counter.kt", "counter.mjs", "counter.test.json"),
    PAYMENTS("payments", "payments.kt", "payments.mjs", "payments.test.json"),
    SOL_VAULT("sol-vault", "sol-vault.kt", "vault.mjs", "sol-vault.test.json"),
    CUSTOM("custom", "custom.kt", "suite.mjs", "custom.test.json"),
    ;

    companion object {
        fun parse(value: String): ProjectTemplate =
            entries.find { it.id == value }
                ?: throw IllegalArgumentException(
                    "Unknown test suite or template '$value'. Choose counter, payments, sol-vault or custom.",
                )
    }
}

internal data class KoralProject(
    val root: File,
    val source: File,
    val output: File,
    val template: ProjectTemplate = ProjectTemplate.COUNTER,
) {
    companion object {
        fun load(directory: File = File(".")): KoralProject {
            val root = directory.canonicalFile
            val config = File(root, "koral.properties")
            require(
                config.isFile,
            ) {
                (
                    "No koral.properties in $root. Run koral init <directory>, then cd into " +
                        "it; or pass an explicit source/binary."
                )
            }
            val values = Properties().apply { config.reader(Charsets.UTF_8).use { load(it) } }
            val format = values.getProperty("format")
            val keys =
                if (format ==
                    "2"
                ) {
                    setOf("format", "source", "output", "test")
                } else {
                    setOf("format", "source", "output")
                }
            require(format in setOf("1", "2") && values.stringPropertyNames() == keys) {
                (
                    "koral.properties requires format=1 with source/output, or format=2 with " +
                        "source/output/test; unknown settings are not supported"
                )
            }
            val template =
                if (format ==
                    "2"
                ) {
                    ProjectTemplate.parse(values.getProperty("test"))
                } else {
                    ProjectTemplate.COUNTER
                }

            fun relative(key: String): File {
                val value = values.getProperty(key)
                require(value.isNotBlank() && !File(value).isAbsolute) {
                    "$key must be a non-empty project-relative path"
                }
                val file = File(root, value).canonicalFile
                require(file != root && file.toPath().startsWith(root.toPath())) {
                    "$key must stay inside the project directory"
                }
                return file
            }
            val source = relative("source")
            val output = relative("output")
            require(source.extension == "kt") { "Project source must be a .kt file" }
            require(output != source && output != source.parentFile) {
                "Project output must be separate from the source"
            }
            return KoralProject(root, source, output, template)
        }

        private fun templateReadme(template: ProjectTemplate): String =
            """
            # Koral ${template.id} project

            Run in this directory with JDK 17+, the pinned Agave SBF SDK and Node 22.12+:

            ```sh
            koral doctor
            koral setup
            koral check
            koral build
            koral test
            ```

            Set KORAL_SBF_SDK to the SDK containing c/sbf.ld. setup installs pinned VM
            dependencies into your user cache with network access; build/test do not install them.
            Source: src/${template.sourceFile}. Output: build/koral. Test report: ${template.reportFile}.
            The initial contract is copied from the bundled ${template.id} example. It includes
            deliberate failure instructions so the matching suite can verify rollback and permissions.

            test runs the ${template.id} contract's fixed regression suite against your actual SBF.
            It is not a general test generator or a security audit. Preserve the suite's expected
            instruction names, state fields, PDA seed and semantics, or write an independent VM test
            when changing that contract. A valid compilation alone does not imply these tests pass.
            Failed tests remove the selected report; rebuilds remove default project reports. No RPC, wallet, deployment or live funds.

            Original Koral materials remain subject to the LICENSE supplied with the CLI.
            """.trimIndent() + "\n"

        private fun projectReadme(template: ProjectTemplate): String =
            if (template == ProjectTemplate.CUSTOM) {
                """
                # Your Koral program

                Run koral doctor, koral setup, koral check, koral build and koral test here.
                Set KORAL_SBF_SDK to the installed Agave SBF SDK. JDK17+ and Node22.12+
                with npm are required. setup explicitly installs pinned VM dependencies.

                Edit src/custom.kt and tests/program.test.mjs together. test executes your
                trusted local JavaScript file with KORAL_TEST_BINARY and KORAL_TEST_CLIENT
                supplied by the CLI. The bundled ABI client handles arguments, accounts,
                PDA derivation, state decoding and a local VM; it does not generate assertions.
                This is user-authored code execution, not an automatic contract audit.
                Keep independent price/state/rollback expectations in your tests.

                Result: build/koral/custom.test.json. Failed runs remove the old result.
                The report records script completion, not independently proven coverage.
                The sample identities and balances are local VM fixtures, never real wallets.
                No RPC or deployment feature is provided. See docs/CUSTOM-PROGRAM.md in
                the CLI installation for the client API, limitations and recovery steps.
                Original Koral materials remain subject to the supplied LICENSE.
                """.trimIndent() + "\n"
            } else if (template != ProjectTemplate.COUNTER) {
                templateReadme(template)
            } else {
                """
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
                """.trimIndent() + "\n"
            }

        fun create(
            directory: File,
            template: ProjectTemplate = ProjectTemplate.COUNTER,
        ): KoralProject {
            val target = directory.canonicalFile.toPath()
            require(
                !Files.exists(target),
            ) { "Destination already exists: $target. Choose a new directory; existing files are never replaced." }
            require(Files.isDirectory(target.parent)) { "Parent directory does not exist: ${target.parent}" }
            val suppliedSource =
                if (template == ProjectTemplate.COUNTER) {
                    null
                } else {
                    requireNotNull(
                        KoralProject::class.java.getResourceAsStream("/koral/templates/${template.sourceFile}"),
                    ) {
                        "Missing ${template.id} project template; reinstall the complete CLI distribution"
                    }.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            val suppliedTest =
                if (template != ProjectTemplate.CUSTOM) {
                    null
                } else {
                    requireNotNull(
                        KoralProject::class.java.getResourceAsStream("/koral/templates/custom-template.test.mjs"),
                    ) {
                        "Missing custom test starter; reinstall the complete CLI distribution"
                    }.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            Files.createDirectory(target)
            Files.createDirectory(target.resolve("src"))
            target.resolve("src/${template.sourceFile}").toFile().writeText(
                suppliedSource
                    ?:
                        """
                        package example
                        import dev.koral.api.*

                        @Account("counter")
                        data class Counter(val count: ULong)

                        @Instruction
                        fun increment(state: Counter, by: ULong): Counter =
                            Counter(checkedAdd(state.count, by))
                        """.trimIndent() + "\n",
            )
            target.resolve("koral.properties").toFile().writeText(
                if (template == ProjectTemplate.COUNTER) {
                    "format=1\nsource=src/counter.kt\noutput=build/koral\n"
                } else {
                    "format=2\nsource=src/${template.sourceFile}\noutput=build/koral\ntest=${template.id}\n"
                },
            )
            target.resolve(".gitignore").toFile().writeText("/build/\n*.keypair.json\n*-keypair.json\n")
            if (template == ProjectTemplate.CUSTOM) {
                Files.createDirectory(target.resolve("tests"))
                target.resolve("tests/program.test.mjs").toFile().writeText(requireNotNull(suppliedTest))
            }
            target.resolve("README.md").toFile().writeText(projectReadme(template))
            return load(target.toFile())
        }
    }
}

internal data class Options(
    val positional: String?,
    val values: Map<String, String>,
) {
    companion object {
        fun parse(
            arguments: List<String>,
            allowed: Set<String>,
        ): Options {
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
