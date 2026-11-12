package dev.koral.api

object Language {
    fun source(): String = requireNotNull(Language::class.java.getResourceAsStream("/koral/Api.kt")) {
        "Bundled language API is missing"
    }.bufferedReader().use { it.readText() }
}
