# Koral Compiler Preview

An experimental Kotlin compiler for native Solana SBF. This package contains the CLI, its JAR dependencies, source examples, local VM tests, and a supported-language guide. It does not include the JDK, Agave SDK, Node.js, or Rust benchmark sources.

## Requirements

JDK 17 or later to run the CLI, an installed Agave 3.1.10 SBF SDK with platform-tools 1.52, and Node >=22.12 for local VM tests. Verified on macOS arm64; other hosts have not been validated in this preview.

## First run

From the extracted package directory:

```sh
export KORAL_SBF_SDK=/path/to/solana-release/bin/platform-tools-sdk/sbf
bin/koral doctor
bin/koral check examples/typed-counter.kt
bin/koral build examples/typed-counter.kt --out build/counter
npm ci --prefix testing
bin/koral test build/counter/counter.so
```

The SDK directory must contain `c/inc`, `c/sbf.ld`, and `dependencies/platform-tools`. Windows launchers are packaged by Gradle, but Windows functionality has not been tested.

## Payment example

```sh
bin/koral build examples/payments.kt --out build/payments
node testing/payments.mjs build/payments/counter.so build/payments/tests.json
```

This executes in local LiteSVM with deterministic test signers. It does not deploy, connect to an RPC, or move real funds. The example transfers from a signing authority through the System Program and records owned state; it is not a production payment application.

Build outputs retain the early `counter` basename: `.so`, `.c`, `.ir`, `.build.json`, and `.abi.json`. The ABI is a preview schema, not an Anchor IDL. In an extracted distribution, read `docs/LANGUAGE.md`; in a source checkout, read `../docs/LANGUAGE.md`.

This preview does not implement general Kotlin, SPL Token, PDA-signed token transfers, arbitrary CPI, or DEX programs. No production-readiness or audit claim is made. Koral's original materials are proprietary, with all rights reserved; see LICENSE. Third-party attribution is in THIRD_PARTY_NOTICES.md.
