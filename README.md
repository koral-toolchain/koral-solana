# Koral

An experimental Kotlin compiler for Solana. Current targets are Counter and a restricted owned ULong-state account, with external balance reads, optional fixed owner/address constraints, and System transfer CPI. This is a development build, not a general Kotlin implementation or a production release.

```text
Kotlin source
  -> pinned Kotlin compiler and Koral IR frontend
  -> Koral model and checks
  -> Koral C backend and Counter runtime
  -> Clang/LLVM + public Agave C SDK
  -> SBF .so
```

TypeSOL is used only in comparative benchmarks. The compiler, runtime, CLI, examples, and local tests do not import or invoke it. Kotlin source is inspected as compiler IR; user JVM code is not executed.

## Launch scope

[The frozen MVP](docs/MVP.md) is Counter plus one authority-funded System payment example. Broader token/DEX features are post-launch work. [The payment comparison](benchmarks/results/payment-mvp/REPORT.md) replays identical signed calls against independent Native Rust and Pinocchio programs, with absolute behavior and rollback checks. The public release and final design are separate pending steps.

## Developer guides

Start with [the developer documentation](docs/guides/index.md): setup, supported Kotlin, account and transfer semantics, CLI artifacts, testing, architecture, and browser-preview boundaries. These guides are rendered into the separate static website; the utility remains independent.

## Build and try

Requirements: JDK 17 (Gradle toolchain), Node >=22.12 for VM tests, and an installed Agave SBF C SDK. The full integration suite was tested on macOS arm64 with Kotlin 2.2.21, Gradle 8.14.6, Agave 3.1.10/platform-tools 1.52, Clang 20.1.7, Node 22.22.3, and LiteSVM 1.6.1. Counter/payment CLI checks also passed on JDK 21. Separate Linux x64 website workers verified the two fixed examples; this is not a completed cross-platform qualification matrix. Check the Product CI workflow results for hosted verification of a particular revision.

From this source checkout, build the CLI and use its installed launcher:

```sh
./gradlew test :cli:installDist
export PATH="$PWD/cli/build/install/koral/bin:$PATH"
export KORAL_SBF_SDK=/path/to/solana-release/bin/platform-tools-sdk/sbf
koral doctor
koral init hello-counter
cd hello-counter
koral setup
koral check
koral build
koral test
```

[The first-project guide](docs/FIRST-PROJECT.md) covers an extracted CLI package,
SDK installation, expected results and failure recovery. setup explicitly installs
pinned Node VM tools into a user cache; it does not modify the installed CLI.
Project commands read source/output from koral.properties. The existing explicit
source/binary commands remain supported.

The SDK path must contain `c/inc`, `c/sbf.ld`, and `dependencies/platform-tools`. Koral does not download it, deploy a program, create a wallet, or sign a live transaction. The test runner uses deterministic local test signers only.

The installed distribution includes JARs, runtime resources, examples, and a standalone Node testing package. Move that directory to another location and run its `bin/koral`. The source project can also be copied without the studio, reference checkouts, or archived experiments.

## Project structure

| Directory | Responsibility |
| --- | --- |
| `language/` | Public `@Instruction`, checked arithmetic, and `ensure` API |
| `compiler/` | Resolved Kotlin IR frontend, internal model, validation, own C backend |
| `toolchain/` | SDK validation and argument-based Clang/LLVM invocation |
| `cli/` | Project initialization, environment diagnosis, VM setup, source/build commands and local Counter verification |
| `runtime/` | Counter account ABI, authorization, initialization, and SDK calls |
| `testing/` | Independent LiteSVM safety and source-semantics checks |
| `examples/` | Supported source examples |
| `benchmarks/` | TypeSOL comparison adapter and recorded measurements |
| `docs/` | Language contract, architecture, limitations, and next steps |

There is one compiler Gradle module, with `frontend`, `model`, `checks`, and `backend.c` packages. These are internal boundaries; additional published modules are unnecessary at this stage. The JVM hosts the compiler; the Solana artifact contains native SBF code and no JVM.

## Current language contract

```kotlin
@Account("pool")
data class PoolState(val reserve_a: ULong, val reserve_b: ULong)

@Instruction
fun add_reserves(state: PoolState, a: ULong, b: ULong): PoolState =
    PoolState(checkedAdd(state.reserve_a, a), checkedAdd(state.reserve_b, b))
```

One data class can declare 1..16 immutable ULong fields. A file supports 1..32 update instructions, with up to eight ULong values and eight external account references each. The runtime still supplies an implicit signing authority, one owned state account, and PDA initialization. Scalar/state locals, conditions, field reads, and checked add/subtract/multiply are supported. The original Counter functions remain compatible.

`AccountInfo`/`WritableAccount` parameters reference caller accounts. `lamports` reads their balances; `transferLamports` makes an actual authority-funded System Program CPI. [The payment example](examples/payments.kt) demonstrates it. This is not arbitrary CPI, SPL Token support, a DEX, or a production payment product.

Builds write C, a bounded model, runtime header, SBF executable, hash manifest, and `counter.abi.json`. The preview ABI describes actual instruction data and account order. Failed rebuilds remove old success artifacts, including ABI JSON. See [the full supported language and ABI](docs/LANGUAGE.md).

## Verification

```sh
./gradlew test :cli:installDist
npm ci --prefix testing
node testing/verify.mjs cli/build/install/koral/bin/koral "$KORAL_SBF_SDK"
```

The integration suite builds different Kotlin programs, checks their real SBF behavior, verifies source changes affect output, rejects unsupported input, and checks stale artifacts after a failed rebuild. The separate Counter safety suite covers account permissions, ownership, authorization, canonical PDA initialization, rollback, and malformed instruction data. Passing these tests does not establish an audit or production security.

[Current Counter comparison](benchmarks/results/system-cpi/REPORT.md): native Rust initialization/increment 4,601/857 CU; Pinocchio 3,186/113 CU; typed-state Koral 3,366/107 CU. The additional internal TypeSOL baseline measured 1,856/110 CU. All five compared final Counter states match. Initialization trails Pinocchio, and small Counter differences do not establish DEX performance.

The separate [payment comparison](benchmarks/results/payment-mvp/REPORT.md) covers the selected authority-funded payment instruction against Native Rust and Pinocchio. It checks behavior and rollback; initialization and whole-program binary-size rankings are outside that comparison. Recorded measurements concern these fixtures, not general payment or DEX performance.

## Additional benchmark cases

Three selected contract operations are also compared: quota consumption, two-reserve accounting, and two-recipient System transfer. They use the existing compiler subset and do not expand the launch promise. See [the 102-transaction comparison](benchmarks/results/three-contracts/REPORT.md) and [reproduction guide](benchmarks/contracts/README.md). They are not full mainnet protocol ports.

## Development boundary

Read [architecture and roadmap](docs/ARCHITECTURE.md) and [third-party notices](THIRD_PARTY_NOTICES.md). Older shared-backend DEX experiments are retained in the internal studio archive, outside the product upload. They cannot establish performance of this compiler and are not required for current setup commands.

The separate [Koral website](https://koral.tools) hosts documentation and a restricted Counter/payment preview. It is not a compiler dependency or a public package release. No mainnet deployment is performed by this project.

## License and contributions

Koral's original code is proprietary, with [all rights reserved](LICENSE).
External modification and redistribution require prior written permission;
external contributions are not currently accepted. Third-party dependencies
retain their own licenses. See [contribution status](CONTRIBUTING.md) and
[the CI contract](docs/CI.md).
