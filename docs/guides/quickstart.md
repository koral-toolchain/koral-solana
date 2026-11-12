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

## Create and verify a Counter project

After building the installed CLI above, add its bin directory to PATH. From the
source checkout root:

```sh
export PATH="$PWD/cli/build/install/koral/bin:$PATH"
koral init hello-counter
cd hello-counter
koral setup
koral check
koral build
koral test
```

setup explicitly downloads the locked Node VM dependencies into your user cache.
It requires network access; build and test do not install anything automatically.
The installed CLI is unchanged. KORAL_CACHE_DIR overrides the cache location.

The generated koral.properties selects src/counter.kt and build/koral, so commands
need no repeated paths. init never overwrites an existing directory. The Counter
verifier should report 55 checks and write build/koral/counter.test.json. This is
not a general test command for arbitrary programs.

For an extracted package instead of a source checkout, follow
[the full first-project guide](../FIRST-PROJECT.md) and [SDK setup](../SDK-SETUP.md).
The original explicit-source commands remain supported:

```sh
koral check /path/to/typed-counter.kt
koral build /path/to/typed-counter.kt --out /path/to/output
koral test /path/to/output/counter.so
```

## Inspect the result

Open counter.c, counter.ir, counter.build.json, and counter.abi.json beside counter.so. All builds currently retain the counter basename, including payment builds. These commands execute locally; they do not deploy a program or transfer real funds.

If using an extracted CLI distribution rather than the source checkout, use bin/koral and its bundled examples/testing directories. The source-checkout Gradle command is not required inside an extracted distribution.
