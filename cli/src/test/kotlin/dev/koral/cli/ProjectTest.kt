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
