import java.security.MessageDigest

plugins {
    kotlin("jvm")
    application
}
dependencies {
    implementation(project(":pipeline"))
    implementation(project(":toolchain"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("com.tngtech.archunit:archunit:1.4.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
application {
    mainClass.set("dev.koral.cli.MainKt")
    applicationName = "koral"
}
distributions.main {
    contents {
        from(project.file("README.md"))
        from(rootProject.file("THIRD_PARTY_NOTICES.md"))
        from(rootProject.file("LICENSE"))
        from(rootProject.file("licenses")) { into("licenses") }
        from(rootProject.file("docs/LANGUAGE.md")) { into("docs") }
        from(rootProject.file("docs/JVM-PIPELINE.md")) { into("docs") }
        from(rootProject.file("docs/FIRST-PROJECT.md")) { into("docs") }
        from(rootProject.file("docs/VAULT.md")) { into("docs") }
        from(rootProject.file("docs/CUSTOM-PROGRAM.md")) { into("docs") }
        from(rootProject.file("docs/ROADMAP.md")) { into("docs") }
        from(rootProject.file("docs/MVP.md")) { into("docs") }
        from(rootProject.file("docs/consumer-reviews/0.3.0-preview.1.md")) { into("docs/consumer-reviews") }
        from(rootProject.file("docs/examples/INVENTORY.md")) { into("docs/examples") }
        from(rootProject.file("docs/SDK-SETUP.md")) { into("docs") }
        from(rootProject.file("testing")) {
            into("testing")
            exclude("node_modules/**", "*-benchmark.mjs")
        }
        from(rootProject.file("examples")) { into("examples") }
    }
}

// Bundle the exact maintained examples; generated projects do not read the checkout.
tasks.processResources {
    from(rootProject.file("examples")) {
        include("payments.kt", "sol-vault.kt", "custom.kt")
        into("koral/templates")
    }
}

tasks.processResources {
    from(rootProject.file("testing/custom-template.test.mjs")) { into("koral/templates") }
}

val runtimeManifest =
    tasks.register("writeRuntimeManifest") {
        val destination = layout.buildDirectory.file("distributions/runtime-classpath.json")
        val ownJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
        val runtime = configurations.named("runtimeClasspath")
        dependsOn(ownJar, runtime)
        inputs.files(ownJar, runtime)
        outputs.file(destination)
        doLast {
            val jars = (runtime.get().files + ownJar.get().asFile).sortedBy { it.name }
            require(jars.map { it.name }.distinct().size == jars.size) { "Duplicate runtime JAR filename" }
            val libraries =
                jars.joinToString(",\n") { jar ->
                    val digest = MessageDigest.getInstance("SHA-256").digest(jar.readBytes())
                    val hash = digest.joinToString("") { "%02x".format(it) }
                    "    \"${jar.name}\": \"$hash\""
                }
            destination.get().asFile.apply {
                parentFile.mkdirs()
                writeText("{\n  \"schema\": \"koral-runtime-classpath/1\",\n  \"libraries\": {\n$libraries\n  }\n}\n")
            }
        }
    }
tasks.named("distTar") { dependsOn(runtimeManifest) }
