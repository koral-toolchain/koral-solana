# JVM compiler pipeline

Status: source/candidate API, not a new Maven release. The existing public
0.3.0-preview.1 archive predates this refactor. There is no new language capability,
ABI schema, native runtime or supported-platform claim.

`dev.koral.pipeline.CompilerPipeline` is the shared entry point for CLI and JVM
callers. It analyzes supported Kotlin through the pinned compiler, validates its
IR, writes C, invokes the existing SBF SDK and writes ABI/build artifacts.

```kotlin
val checked = CompilerPipeline.check(File("src/counter.kt"))
val result = CompilerPipeline.build(
    File("src/counter.kt"),
    File("build/koral"),
    File("/path/to/sbf-sdk"),
)
println(result.summary.instructionCount)
println(result.binary)
```

For an in-repository Gradle consumer, use `implementation(project(":pipeline"))`.
No Maven coordinates have been published for this module. An extracted candidate
contains all required JARs under lib; Java consumers can compile/run against that
complete classpath. Hosted consumer jobs demonstrate this with PipelineSmoke.java.
The SDK remains separate; omitting sdk uses KORAL_SBF_SDK. No wallet, live network
or CLI handler is required.

Results expose a small summary and artifact files rather than leaking the internal
IR through the pipeline API. Source diagnostics and existing check/build CLI output
remain unchanged. Builds invalidate old binary/ABI/manifest success artifacts before
analysis and remove partial success artifacts on failure. CLI VM reports remain
owned by the CLI. Mutable C-output state is created per build. No thread-safety or
concurrent-build guarantee is made for the same output directory.

Package names describe responsibilities: kotlin, ir, validation, target.c and abi.
The pipeline coordinates their calls rather than making analysis invoke output.
A shared representation belongs in IR; new cross-role dependencies need a reason
and a governed rule change. The exact allowed dependencies and real negative
controls are listed in [engineering rules](https://github.com/koral-toolchain/koral-solana/blob/main/docs/ENGINEERING.md). No dependency-injection
framework, JNI bridge or new compiler process was added by the refactor.
