plugins { kotlin("jvm"); application }
dependencies {
    implementation(project(":language"))
    implementation(project(":compiler"))
    implementation(project(":toolchain"))
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
        from(rootProject.file("docs/LANGUAGE.md")) { into("docs") }
        from(rootProject.file("testing")) { into("testing"); exclude("node_modules/**") }
        from(rootProject.file("examples")) { into("examples") }
    }
}
