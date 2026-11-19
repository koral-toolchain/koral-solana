# Install and run your first Counter

The 0.2.0-preview.1 development preview provides a CLI archive and local Counter,
payment and SOL-vault workflows.
Koral remains proprietary with all rights reserved. Read the license and known limits in
[the release notes](https://github.com/koral-toolchain/koral-solana/releases/tag/v0.2.0-preview.1).

## Download and requirements

[Download the CLI TAR](https://github.com/koral-toolchain/koral-solana/releases/download/v0.2.0-preview.1/koral-0.2.0-preview.1.tar) and check it against
[SHA256SUMS](https://github.com/koral-toolchain/koral-solana/releases/download/v0.2.0-preview.1/SHA256SUMS).

Use JDK 17+, Node 22.12+ with npm for VM tests, and the external Agave 3.1.10 /
platform-tools 1.52 SBF SDK. Node is not required to compile. The release records
the exact tested Linux x64 and macOS arm64 runtime pairs. Follow the
[SDK installation guide](https://github.com/koral-toolchain/koral-solana/blob/v0.2.0-preview.1/docs/SDK-SETUP.md) if needed.

## Install the CLI

```sh
tar -xf koral-0.2.0-preview.1.tar
export PATH="$PWD/koral-0.2.0-preview.1/bin:$PATH"
export KORAL_SBF_SDK=/path/to/solana-release/bin/platform-tools-sdk/sbf
koral doctor
```

Replace the SDK path with your installation. doctor prints the available tools and
next steps; it never installs anything. The CLI archive does not bundle JDK, SDK
or Node. You do not need Gradle or the compiler source checkout to use the archive.

## Create, compile and verify

From a writable directory outside the installed CLI:

```sh
koral init hello-counter
cd hello-counter
koral setup
koral check
koral build
koral test
```

init refuses an existing directory and creates src/counter.kt, koral.properties,
README.md and .gitignore. Project configuration supplies the source/output paths.
setup explicitly installs locked VM dependencies into your user cache. It requires
network access and leaves the installed CLI unchanged. KORAL_CACHE_DIR overrides
the cache root. build and test do not install dependencies automatically.

A successful test reports 55 Counter checks and writes build/koral/counter.test.json.
The verifier is specific to the supplied Counter contract. It runs locally without
a wallet, RPC, deployment or real funds. Passing tests is not a security audit.

## Payment and SOL vault projects

To create a separate payment project, run this from a writable parent directory:

```sh
koral init hello-payments --template payments
cd hello-payments
koral check
koral build
koral test
```

Run setup first if the VM tools are not already installed. Use --template sol-vault
for a separate vault project. Payment verification checks 37 cases; vault
verification checks 101, including permissions, actual-size rent retention and
rollback. These fixed suites require their template's instruction/state/seed
contract, rather than generating tests for arbitrary edited code.

The website's fixed-example worker is separate and still runs its deployed
0.1.0-dev image. The new vault workflow is available through the downloaded CLI.

## Inspect and recover

Inspect counter.so, counter.c, counter.ir, counter.abi.json and counter.build.json in
build/koral. Failed rebuilds invalidate old binary/ABI/success metadata and default test reports.
A failed test removes its selected result report. Missing
project, SDK, binary or VM tools produces an actionable diagnostic.

The original explicit-source commands remain available. Read
[the complete first-project guide](https://github.com/koral-toolchain/koral-solana/blob/v0.2.0-preview.1/docs/FIRST-PROJECT.md)
for configuration, legacy commands, source builds and failure recovery.
