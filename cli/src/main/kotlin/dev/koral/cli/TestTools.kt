package dev.koral.cli

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal fun capture(command: List<String>, directory: File? = null): String? {
    val log = File.createTempFile("koral-probe-", ".log")
    return try {
        val process = ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            null
        } else if (process.exitValue() == 0) log.readText().trim() else null
    } catch (_: java.io.IOException) { null } finally { log.delete() }
}

internal fun supportedNode(version: String?): Boolean {
    val match = version?.let { Regex("^v(\\d+)\\.(\\d+)\\.\\d+.*$").matchEntire(it) } ?: return false
    val major = match.groupValues[1].toIntOrNull() ?: return false
    val minor = match.groupValues[2].toIntOrNull() ?: return false
    return major > 22 || major == 22 && minor >= 12
}

internal class TestTools(private val home: File, cacheRoot: File = File(System.getenv("KORAL_CACHE_DIR") ?: File(System.getProperty("user.home"), ".cache/koral").path)) {
    private val bundled = File(home, "testing")
    private fun files(): List<File> {
        require(bundled.isDirectory) { "Missing testing tools in $home; reinstall the complete CLI distribution" }
        val files = requireNotNull(bundled.listFiles()) { "Cannot read the installed VM tools in $bundled" }.filter { it.isFile && (it.extension == "mjs" || it.name in setOf("package.json", "package-lock.json")) }.sortedBy { it.name }
        require(files.any { it.name == "package-lock.json" } && files.any { it.name == "counter.mjs" }) { "Incomplete testing tools; reinstall the CLI distribution" }
        return files
    }
    val cache: File by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        files().forEach { digest.update(it.name.toByteArray()); digest.update(0.toByte()); digest.update(it.readBytes()) }
        val identity = digest.digest().joinToString("") { "%02x".format(it) }.take(24)
        val platform = (System.getProperty("os.name") + "-" + System.getProperty("os.arch")).replace(Regex("[^A-Za-z0-9._-]"), "_")
        File(cacheRoot, "vm/$platform-$identity").canonicalFile
    }
    fun ready(): Boolean = File(cache, ".ready").isFile
    fun setup() {
        require(supportedNode(capture(listOf("node", "--version")))) { "Node 22.12+ is required for VM tests. Install Node with npm, then run koral setup again. The compiler itself does not need Node." }
        require(capture(listOf("npm", "--version")) != null) { "npm was not found. Install Node with npm, then run koral setup again." }
        check(cache.isDirectory || cache.mkdirs() || cache.isDirectory) { "Cannot create VM cache at $cache. Set KORAL_CACHE_DIR to a writable directory." }
        FileChannel.open(File(cache, ".setup.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            val lock = channel.tryLock() ?: error("Another koral setup is using $cache; wait for it to finish")
            lock.use {
                File(cache, ".ready").delete()
                files().forEach { it.copyTo(File(cache, it.name), overwrite = true) }
                println("Installing pinned local VM tools into $cache")
                val process = ProcessBuilder("npm", "ci", "--ignore-scripts", "--no-audit", "--no-fund", "--prefix", cache.path).inheritIO().start()
                if (!process.waitFor(240, TimeUnit.SECONDS)) { process.destroyForcibly(); error("VM dependency installation timed out; run koral setup again") }
                check(process.exitValue() == 0) { "VM dependency installation failed; check network access and run koral setup again" }
                check(healthy(cache)) { "The installed VM dependencies could not load on this Node/platform. Run koral doctor and check the supported platforms." }
                File(cache, ".ready").writeText("Pinned dependencies installed and imported successfully.\n")
            }
        }
        println("VM tools ready. In your project, run koral build, then koral test.")
    }
    fun healthy(directory: File? = runnerDirectoryOrNull()): Boolean {
        if (directory == null) return false
        return capture(listOf("node", "--input-type=module", "-e", "await import('@solana/kit'); await import('litesvm'); console.log('ready')"), directory)?.lineSequence()?.any { it == "ready" } == true
    }

    private fun runnerDirectoryOrNull(): File? = when {
        ready() -> cache
        File(bundled, "node_modules").isDirectory -> bundled
        else -> null
    }
    fun runner(): File {
        require(supportedNode(capture(listOf("node", "--version")))) { "Node 22.12+ is required for local VM tests. Run koral doctor for setup instructions." }
        val directory = runnerDirectoryOrNull() ?: error("VM dependencies are not installed. Run koral setup once (requires npm and network access), then koral test again.")
        return File(directory, "counter.mjs")
    }
}
