package dev.koral.cli

import java.io.File
import java.nio.file.Files
import kotlin.test.*

class ProjectTest {
    private fun inTemporaryDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("koral-project-test-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }

    @Test fun createsCounterProjectWithPathsRelativeToItsOwnDirectory() = inTemporaryDirectory { root ->
        val project = KoralProject.create(File(root, "hello with spaces"))
        assertEquals(File(project.root, "src/counter.kt"), project.source)
        assertEquals(File(project.root, "build/koral"), project.output)
        assertTrue(project.source.readText().contains("checkedAdd(state.count, by)"))
        assertTrue(File(project.root, "README.md").isFile)
        assertFalse(project.output.exists())
    }

    @Test fun createsPaymentAndVaultProjectsFromBundledResources() = inTemporaryDirectory { root ->
        for (template in listOf(ProjectTemplate.PAYMENTS, ProjectTemplate.SOL_VAULT)) {
            val project = KoralProject.create(File(root, "${template.id} with spaces"), template)
            assertEquals(template, project.template)
            assertEquals(File(project.root, "src/${template.sourceFile}"), project.source)
            assertEquals(template, KoralProject.load(project.root).template)
            assertEquals(KoralProject::class.java.getResource("/koral/templates/${template.sourceFile}")!!.readText(), project.source.readText())
            assertTrue(File(project.root,"README.md").readText().contains(template.reportFile))
            assertTrue(File(project.root,"README.md").readText().contains("fixed regression suite"))
            assertFalse(project.output.exists())
        }
    }

    @Test fun customProjectBundlesEditableSourceAndIndependentTestScript() = inTemporaryDirectory { root ->
        val project = KoralProject.create(File(root, "custom project"), ProjectTemplate.CUSTOM)
        assertEquals(ProjectTemplate.CUSTOM, KoralProject.load(project.root).template)
        assertEquals(KoralProject::class.java.getResource("/koral/templates/custom.kt")!!.readText(), project.source.readText())
        val script = File(project.root, "tests/program.test.mjs")
        assertEquals(KoralProject::class.java.getResource("/koral/templates/custom-template.test.mjs")!!.readText(), script.readText())
        script.writeText("// consumer owns these assertions\n")
        assertFailsWith<IllegalArgumentException> { KoralProject.create(project.root, ProjectTemplate.CUSTOM) }
        assertEquals("// consumer owns these assertions\n", script.readText())
        assertTrue(File(project.root, "README.md").readText().contains("user-authored"))
    }

    @Test fun formatOneRemainsCounterAndFormatTwoRequiresAKnownSuite() = inTemporaryDirectory { root ->
        val project = KoralProject.create(File(root,"legacy"))
        assertEquals(ProjectTemplate.COUNTER, project.template)
        assertEquals("format=1\nsource=src/counter.kt\noutput=build/koral\n",File(project.root,"koral.properties").readText())
        for (suite in listOf("", "unknown", "../payments.mjs", "counter;echo hi")) {
            File(root,"koral.properties").writeText("format=2\nsource=src/example.kt\noutput=build/koral\ntest=$suite\n")
            assertFailsWith<IllegalArgumentException> { KoralProject.load(root) }
        }
    }

    @Test fun templatesRefuseToOverwriteExistingSourceAndRejectInvalidNames() = inTemporaryDirectory { root ->
        for(template in ProjectTemplate.entries) {
            val project=KoralProject.create(File(root,template.id),template)
            project.source.writeText("owned source")
            assertFailsWith<IllegalArgumentException> { KoralProject.create(project.root,template) }
            assertEquals("owned source",project.source.readText())
        }
        assertFailsWith<IllegalArgumentException> { ProjectTemplate.parse("payments.mjs") }
    }

    @Test fun neverOverwritesAnExistingProject() = inTemporaryDirectory { root ->
        val project = KoralProject.create(File(root, "hello"))
        project.source.writeText("owner's existing source")
        assertFailsWith<IllegalArgumentException> { KoralProject.create(project.root) }
        assertEquals("owner's existing source", project.source.readText())
    }

    @Test fun missingProjectGivesAnActionableError() = inTemporaryDirectory { root ->
        val error = assertFailsWith<IllegalArgumentException> { KoralProject.load(root) }
        assertTrue(error.message!!.contains("koral init"))
    }

    @Test fun rejectsUnknownAndIncompleteConfiguration() = inTemporaryDirectory { root ->
        for (text in listOf("source=src/counter.kt\noutput=build/koral\n", "format=2\nsource=src/counter.kt\noutput=build/koral\n", "format=1\nsource=src/counter.kt\noutput=build/koral\ncommand=execute\n")) {
            File(root, "koral.properties").writeText(text)
            assertFailsWith<IllegalArgumentException> { KoralProject.load(root) }
        }
    }

    @Test fun rejectsPathsOutsideTheProjectAndSourceOutputOverlap() = inTemporaryDirectory { root ->
        for ((source, output) in listOf("../outside.kt" to "build", "src/counter.kt" to "../outside", "src/counter.kt" to root.absolutePath, "src/counter.kt" to ".", "src/counter.kt" to "src")) {
            File(root, "koral.properties").writeText("format=1\nsource=$source\noutput=$output\n")
            assertFailsWith<IllegalArgumentException> { KoralProject.load(root) }
        }
    }

    @Test fun rejectsSymlinkOutputEscapingTheProject() = inTemporaryDirectory { root ->
        val project = KoralProject.create(File(root, "hello"))
        Files.createSymbolicLink(File(project.root, "build").toPath(), root.toPath())
        assertFailsWith<IllegalArgumentException> { KoralProject.load(project.root) }
    }

    @Test fun parsesSourceAndOptionsWithoutDependingOnTheirOrder() {
        val parsed = Options.parse(listOf("--sdk", "/sdk with spaces", "source.kt", "--out", "out"), setOf("--sdk", "--out"))
        assertEquals("source.kt", parsed.positional)
        assertEquals("/sdk with spaces", parsed.values["--sdk"])
        assertNull(Options.parse(listOf("--sdk", "/sdk"), setOf("--sdk")).positional)
    }

    @Test fun rejectsAmbiguousOrUnsupportedArguments() {
        for (args in listOf(listOf("--sdk"), listOf("--sdk", "--out", "build"), listOf("--sdk", "a", "--sdk", "b"), listOf("a.kt", "b.kt"), listOf("--unknown", "value"))) {
            assertFailsWith<IllegalArgumentException> { Options.parse(args, setOf("--sdk", "--out")) }
        }
    }

    @Test fun enforcesTheActualNodeMinimum() {
        assertFalse(supportedNode(null))
        assertFalse(supportedNode("v20.19.0"))
        assertFalse(supportedNode("v22.11.0"))
        assertFalse(supportedNode("not a version"))
        assertTrue(supportedNode("v22.12.0"))
        assertTrue(supportedNode("v24.22.2"))
    }

    @Test fun cacheIdentityTracksBundledToolsWithoutCreatingFiles() = inTemporaryDirectory { root ->
        val home = File(root, "readonly-install").apply { mkdirs() }
        val testing = File(home, "testing").apply { mkdirs() }
        File(testing, "package-lock.json").writeText("pinned dependency lock")
        File(testing, "counter.mjs").writeText("first verifier")
        val cacheRoot = File(root, "user-cache")
        val first = TestTools(home, cacheRoot).cache
        assertFalse(first.exists())
        assertTrue(first.toPath().startsWith(cacheRoot.canonicalFile.toPath()))
        File(testing, "counter.mjs").writeText("updated verifier")
        assertNotEquals(first, TestTools(home, cacheRoot).cache)
        assertFalse(cacheRoot.exists())
    }
}
