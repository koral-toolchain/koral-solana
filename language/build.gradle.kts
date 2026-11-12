plugins { kotlin("jvm") }
tasks.processResources {
    from("src/main/kotlin/dev/koral/api/Api.kt") { into("koral") }
}
