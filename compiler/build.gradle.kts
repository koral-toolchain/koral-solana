plugins { kotlin("jvm") }
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.21")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
kotlin.compilerOptions { freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi") }
tasks.processResources {
    from(rootProject.file("runtime")) { into("koral/runtime") }
}
