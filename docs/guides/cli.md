# CLI and generated artifacts

The installed executable provides init, setup, check, build, doctor, and test. The current development candidate is 0.2.0-preview.1; it is not yet published.

## Commands

```text
koral init <new-directory> [--template counter|payments|sol-vault]
koral setup
koral check [source.kt]
koral build [source.kt] [--out <directory>] [--sdk <SDK>]
koral doctor [--sdk <SDK>]
koral test [counter.so] [--suite counter|payments|sol-vault]
```

check and build accept one source path, or use koral.properties in the current project when it is omitted. build supports --out and --sdk as distinct option/value pairs. The default output directory is build/koral. An explicit --sdk takes precedence over KORAL_SBF_SDK. doctor reports Java and the installed Clang version. test delegates to the selected bundled contract verifier. Unreleased payment/vault projects select their suite through format=2 configuration; format=1 and explicit binaries without --suite retain Counter. See [the first-project workflow](../FIRST-PROJECT.md).

Errors are written to stderr with the Koral: prefix and a nonzero exit status. There is no deploy command. The Counter, payments and sol-vault suites each require their specific contract. A suite is not a general test generator for arbitrary user code.

## Output files

| File | Content |
| --- | --- |
| counter.c | Generated C plus the runtime |
| counter.ir | Bounded Koral data model, not executable JVM output |
| include/koral.h | Runtime ABI header |
| counter.so | Native Solana SBF executable |
| counter.build.json | Build provenance, hashes, sizes, compiler/toolchain details |
| counter.abi.json | Preview account/instruction schema |

The counter basename is retained even for a differently named source file. The temporary counter.o is deleted after the native toolchain step.

## Preview ABI

The schema is koral-preview-abi/1 targeting sbpf-v0. It includes state type, size, field offsets, discriminator, PDA seed, initialization, instruction discriminators, data offsets, account order and permissions, optional fixed owner/address/data-size/executable constraints, and authority-funded transfer requirements.

This is not an Anchor IDL, a stable published client schema, or a deployed program address. Instruction discriminators are the first eight SHA-256 bytes of global:name. Account discriminators use account:TypeName.

## Failed rebuilds

Before recompiling into an existing output directory, the CLI removes old counter.so, counter.build.json, and counter.abi.json success artifacts, plus the default Counter/payment/vault test reports. Do not treat a leftover C or model file as evidence of a successful build. Check the command exit status and the new success artifacts.

## Provenance and reproducibility

The build manifest hashes source, compiler JAR, runtime, generated C, and binary, and records actual Clang details. This makes a result inspectable; it is not a guarantee of identical bytes on every operating system or SDK version.

Implementation basis: cli/Main.kt, ProgramAbi.kt, and SbfToolchain.kt.
