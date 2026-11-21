plugins {
    base
    kotlin("jvm") version "2.2.21" apply false
    id("com.diffplug.spotless") version "7.2.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint("1.7.1")
    }
    kotlinGradle {
        target("*.gradle.kts", "*/build.gradle.kts")
        ktlint("1.7.1")
    }
}

allprojects {
    group = "dev.koral"
    version = "0.3.0-preview.1"
}
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> { jvmToolchain(17) }
    dependencyLocking { lockAllConfigurations() }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        toolVersion = "1.23.8"
        buildUponDefaultConfig = false
        config.setFrom(rootProject.file("config/detekt.yml"))
    }
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Check pinned Kotlin formatting, static analysis and architecture boundaries."
    dependsOn("spotlessCheck", subprojects.map { "${it.path}:detekt" }, ":cli:test")
}

tasks.matching { it.name == "check" }.configureEach { dependsOn("qualityCheck") }
