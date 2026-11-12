# Architecture and Next Work

## Boundaries

`cli` depends on `language`, `compiler`, and `toolchain`. `compiler` uses the pinned Kotlin compiler library, then its own model, checks, and C backend. It does not import a website, studio checkout, TypeSOL emitter, intermediate TypeScript, or a foreign DEX runtime. TypeSOL is an additional independent benchmark only.

A separate bounded Kotlin compiler process produces resolved IR. Koral checks trusted API declarations and the supported subset, then writes a bounded data-only model (KOR4). Temporary JVM output is never executed. Model validation runs again before C emission. Runtime and API sources are bundled JAR resources; installed CLI execution does not locate a studio root.

The model describes owned ULong state, typed external account references, arithmetic/control flow, and an explicit System transfer statement. Source account names map to safe C parameters. ULong data and external-account order are tracked separately. Every owned state write happens after a successful return; external effects are real CPIs and rely on Solana transaction rollback on failure.

The runtime supplies authority validation, owner/discriminator/size/writable checks, canonical PDA initialization, serialization, account metadata reads, and a bounded System transfer helper. External reference mutability is checked at runtime as well as in the model. The System Program address/executable flag is validated before invocation. Arbitrary programs and PDA signing are not exposed by the transfer API.

`ProgramAbi` exports the resolved source contract as preview JSON. Caller tests use the output to encode actual SBF calls. The compiler does not claim Anchor IDL or generated client compatibility.

The toolchain invokes installed SDK binaries using argument lists, with timeout/output bounds. Kotlin 2.2.21, Gradle 8.14.6, JDK 17 toolchain, wrapper checksum, and dependency lockfiles are pinned. Build manifests hash source, compiler JAR, runtime, emitted C, and SBF and record actual Clang. This is provenance, not a cross-machine reproducible-build guarantee.

## Current implementation

- 1..32 state-update instructions per file.
- One declared data class with 1..16 ULong fields, or the legacy Counter form.
- Per instruction, up to eight ULong arguments and eight external references.
- Scalar/state locals, conditions and returns; checked add/subtract/multiply.
- External lamport reads; signing-authority System transfer CPI.
- Preview ABI output, local VM checks, and separate Counter comparisons.

These features still use a single owned state account and implicit authority/PDA convention. There is no general Kotlin runtime, multiple owned account types, token-account decoding, arbitrary byte data, arbitrary CPI/PDA signing, IDL/client compatibility, RPC service, or deploy command. Output files retain the early `counter` basename for now; a non-Counter state program can also produce `counter.so`.

## Launch priority

[The frozen MVP](MVP.md) is Counter plus one System payment-CPI example and credible Native Rust/Pinocchio comparisons. No further compiler features are required before this technical preview. Finish reproducible benchmarks, package/setup review, and release evidence; keep design separate.

## Post-MVP directions

External public-key/owner constraints and account-data decoding, SPL Token and PDA signing, wider arithmetic and selected DEX instructions remain possible next work. They must not silently become new launch blockers. Stable ABI/language contracts, additional platforms, and external developer feedback can be added incrementally.

The separate design session owns presentation. Only implemented and tested behavior may be described as available.
