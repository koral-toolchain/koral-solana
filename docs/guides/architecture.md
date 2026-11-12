# Inside the compiler

The utility builds independently of the website. Its four Gradle modules are language, compiler, toolchain, and cli. This page follows the actual code path rather than proposing a new architecture.

## Language declarations

language bundles the trusted API source. @Account, @Instruction, checked arithmetic, ensure, lamports, and transferLamports define the supported surface. Their JVM bodies are not a runtime implementation; they fail if called normally.

## Resolved Kotlin IR

KotlinFrontend starts a bounded child Kotlin compiler process with the Koral plugin. The current Kotlin version is pinned to 2.2.21. KoralRegistrar verifies the source file, trusted declarations and symbol origins, declaration shapes, and supported expressions before lowering into its own data model.

Temporary JVM output is never executed. Compilation has a 90-second bound, and its temporary directory is removed afterwards.

## Model and validation

The data-only model includes U64, Boolean, state and external-account reference types, expressions, and statements such as branches, returns, checked arithmetic, and System transfers. ProgramCodec serializes the bounded model. ProgramChecks validates it again before C emission.

Limits are concrete: one declared account, 1..16 fields, 1..32 instructions, and up to eight data arguments plus eight external references per instruction. These are implementation limits, not claims about all Kotlin programs.

## C backend and runtime

CounterC lowers validated operations to C and combines them with the bundled runtime. The runtime supplies authority/state validation, canonical PDA initialization, serialization, lamport reads, and the bounded System transfer helper. Owned state writes follow successful instruction execution; CPI rollback follows the transaction's execution semantics.

## Native toolchain

SbfToolchain invokes installed Clang and ld.lld using argument lists. The C compile uses -O2, C17, the sbf target, warnings as errors, and a 4096-byte frame warning threshold. Linking uses the Agave SBF linker script and bundled SDK compiler builtins. Native tool invocations also have 90-second bounds.

No JVM is embedded in the final program. The toolchain does not publish a release or deploy to a chain.

## Build metadata

Main.kt orchestrates check/build/doctor/test, invalidates stale success artifacts, and writes provenance. ProgramAbi exports the resolved instruction/account contract. Read [CLI and artifacts](cli.md) for the file-level contract.

## Contributor map

| Source path within utility/ | Responsibility |
| --- | --- |
| language/src/main/kotlin/dev/koral/api/Api.kt | Trusted source API |
| compiler/frontend/KotlinFrontend.kt and KoralRegistrar.kt | Compiler process and IR lowering |
| compiler/model/ | Data model and bounded serialization |
| compiler/checks/ProgramChecks.kt | Type and control-flow validation |
| compiler/backend/c/CounterC.kt | C emission |
| compiler/metadata/ProgramAbi.kt | ABI JSON |
| toolchain/.../SbfToolchain.kt | Native SDK tools |
| cli/.../Main.kt | User commands and artifacts |
| runtime/src/counter.c | Account ABI and runtime checks |

Compiler package paths above are abbreviated: compiler implementation files live under compiler/src/main/kotlin/dev/koral/compiler/. The website and web adapter are separate consumers. General account constraints, arbitrary CPI, token decoding, PDA-signed transfers, and full DEX workloads remain outside this preview.
