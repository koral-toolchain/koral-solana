# Third-party license texts

Koral's original materials remain subject to the root LICENSE (all rights
reserved). These third-party notices do not grant rights to Koral's own code.

The kotlin/ directory preserves the complete, unmodified license directory from
the official Kotlin 2.2.21 compiler distribution, including its copyright,
Apache-2.0 license, NOTICE and third-party notices. Keeping the complete upstream
set preserves notices for compiler internals; it does not mean every component
described by upstream is bundled with Koral. Paths in upstream README files refer
to the original Kotlin repository and are preserved as upstream documentation.

Source asset:
https://github.com/JetBrains/kotlin/releases/download/v2.2.21/kotlin-compiler-2.2.21.zip

SHA-256: a623871f1cd9c938946948b70ef9170879f0758043885bbd30c32f024e511714

The CLI runtime JAR inventory is Kotlin compiler/stdlib/daemon/script runtime
2.2.21, kotlin-reflect 1.6.10, kotlinx-coroutines-core-jvm 1.8.0 and JetBrains
annotations 13.0, plus Koral's own modules. The JetBrains libraries are resolved
Apache-2.0 dependencies. The exact dependency graph remains in Gradle lockfiles.
JUnit is test-only and is not bundled in the CLI distribution.

Agave/LLVM and the Node VM dependencies are separately installed inputs rather
than files in this archive. Their upstream terms continue to apply. See
THIRD_PARTY_NOTICES.md in the archive root for the product dependency boundary.
