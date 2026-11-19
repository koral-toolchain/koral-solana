plugins { kotlin("jvm"); application }
dependencies {
    implementation(project(":language"))
    implementation(project(":compiler"))
    implementation(project(":toolchain"))
    testImplementation(kotlin("test-junit5"))
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
        from(rootProject.file("docs/FIRST-PROJECT.md")) { into("docs") }
        from(rootProject.file("docs/VAULT.md")) { into("docs") }
        from(rootProject.file("docs/CUSTOM-PROGRAM.md")) { into("docs") }
        from(rootProject.file("docs/SDK-SETUP.md")) { into("docs") }
        from(rootProject.file("testing")) { into("testing"); exclude("node_modules/**") }
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
