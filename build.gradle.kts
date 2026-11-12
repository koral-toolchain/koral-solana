plugins { kotlin("jvm") version "2.2.21" apply false }
allprojects {
    group = "dev.koral"
    version = "0.1.0-dev"
}
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> { jvmToolchain(17) }
    dependencyLocking { lockAllConfigurations() }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}
