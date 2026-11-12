# Build your first program

This guide starts in a Koral utility source checkout. Commands below run from that directory. No published Gradle plugin or public package coordinate is assumed.

## Requirements

| Requirement | Purpose |
| --- | --- |
| JDK 17 toolchain | Build and run the compiler |
| Node.js 22.12+ | Run local VM verification |
| Agave SBF C SDK | Native Clang/LLVM compilation |
| macOS arm64 | Previously verified development host |

Recorded toolchain: Kotlin 2.2.21, Gradle 8.14.6, Agave 3.1.10 / platform-tools 1.52, and LiteSVM 1.6.1. Other hosts are not verified by this guide.

## Build the CLI

```sh
./gradlew test :cli:installDist
export KORAL_SBF_SDK=/path/to/solana-release/bin/platform-tools-sdk/sbf
cli/build/install/koral/bin/koral doctor
```

Replace the SDK path with your local installation. It must provide c/inc, c/sbf.ld, and dependencies/platform-tools. Koral does not install the SDK for you.

## Check and compile

```sh
cli/build/install/koral/bin/koral check examples/typed-counter.kt
cli/build/install/koral/bin/koral build examples/typed-counter.kt --out build/counter
```

The check command validates source without requiring an SBF SDK. The build command also invokes native tools. Keep the output directory separate from the source directory.

## Run the Counter verification

```sh
npm ci --prefix cli/build/install/koral/testing
cli/build/install/koral/bin/koral test build/counter/counter.so
```

The test command is Counter-specific. It is not a general test runner for every possible state layout or payment program. [Testing and errors](testing.md) explains the separate payment verifier.

## Inspect the result

Open counter.c, counter.ir, counter.build.json, and counter.abi.json beside counter.so. All builds currently retain the counter basename, including payment builds. These commands execute locally; they do not deploy a program or transfer real funds.

If using an extracted CLI distribution rather than the source checkout, use bin/koral and its bundled examples/testing directories. The source-checkout Gradle command is not required inside an extracted distribution.
