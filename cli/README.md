# Koral Compiler Preview

An experimental Kotlin compiler for native Solana SBF. This package contains the CLI, its JAR dependencies, source examples, local VM tests, and a supported-language guide. It does not include the JDK, Agave SDK, Node.js, or Rust benchmark sources.

## Requirements

JDK 17 or later to run the CLI, an installed Agave 3.1.10 SBF SDK with platform-tools 1.52, and Node >=22.12 for local VM tests. The consumer workflow checks the candidate on the runtime pairs documented in the source repository docs/CI.md; inspect the run for your revision.

## First run

Extract the complete package and add its bin directory to PATH. In a writable
workspace outside that installation:

```sh
export PATH="/path/to/koral-0.1.0-dev/bin:$PATH"
export KORAL_SBF_SDK=/path/to/solana-release/bin/platform-tools-sdk/sbf
koral doctor
koral init hello-counter
cd hello-counter
koral setup
koral check
koral build
koral test
```

The SDK directory must contain c/inc, c/sbf.ld and dependencies/platform-tools.
Read docs/FIRST-PROJECT.md and docs/SDK-SETUP.md in this package for installation
and expected results. setup explicitly installs locked VM tools into the user
cache; build/test do not download dependencies. Node is only needed for VM tests.
KORAL_CACHE_DIR overrides that cache root. Existing explicit source/binary CLI
arguments and locally installed testing/node_modules remain supported.

The generated koral.properties selects src/counter.kt and build/koral. init
refuses existing directories. test is specific to the supplied Counter and writes
its result into build/koral/counter.test.json. Windows functionality is unverified.

## Payment example

From the extracted package directory, the direct payment verifier uses its bundled testing directory:

```sh
bin/koral build examples/payments.kt --out build/payments
npm ci --ignore-scripts --prefix testing
node testing/payments.mjs build/payments/counter.so build/payments/tests.json
```

This executes in local LiteSVM with deterministic test signers. It does not deploy, connect to an RPC, or move real funds. The example transfers from a signing authority through the System Program and records owned state; it is not a production payment application.

Build outputs retain the early `counter` basename: `.so`, `.c`, `.ir`, `.build.json`, and `.abi.json`. The ABI is a preview schema, not an Anchor IDL. In an extracted distribution, read `docs/LANGUAGE.md`; in a source checkout, read `../docs/LANGUAGE.md`.

This preview does not implement general Kotlin, SPL Token, PDA-signed token transfers, arbitrary CPI, or DEX programs. No production-readiness or audit claim is made. Koral's original materials are proprietary, with all rights reserved; see LICENSE. Third-party attribution is in THIRD_PARTY_NOTICES.md.
