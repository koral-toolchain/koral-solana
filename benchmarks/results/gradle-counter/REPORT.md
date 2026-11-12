# Counter: Gradle Compiler Implementation

Measured 2026-11-11 on macOS arm64. Koral used its own Kotlin IR frontend, model, checks, C backend, and Counter runtime. TypeSOL was invoked only by this separate comparison adapter.

| Operation | TypeSOL 0.43.0 | Anchor | Native Rust | Pinocchio | Koral |
| --- | ---: | ---: | ---: | ---: | ---: |
| Initialize CU | 1,856 | 6,573 | 4,601 | 3,186 | 3,359 |
| Increment CU | 110 | 2,323 | 857 | 113 | 109 |
| ELF size, KiB rounded | 11.1 | 145.2 | 27.9 | 19.6 | 7.6 |

Koral ELF: 7,808 bytes; SHA-256 `d57934fbb6d7196e8399143c2c8703516bb59de9b5bba646c7f19425ba961692`. Its Counter state is 49 bytes, matching the comparison. The one-CU increment difference is small. Initialization currently costs about 81% more CU than TypeSOL; this implementation uses the public SDK PDA syscall rather than the earlier experimental fast PDA implementation.

## Conditions and provenance

- Pinned TypeSOL checkout: `543786e850ad426425956e497971f88103f6c987`, version 0.43.0.
- Upstream benchmark checkout: `05fb4e1a56c2447becd7a017b3a8b941d2d43273`; original runner SHA-256 in `provenance.json`.
- Koral: Kotlin 2.2.21, Gradle 8.14.6, JDK 17 bytecode/toolchain, `-O2`, SBPF v0, Agave 3.1.10/platform-tools 1.52, Clang 20.1.7. Source/runtime/compiler JAR/C/ELF hashes are in `koral-build.json`.
- Local VM: LiteSVM 1.6.1, Node 22.22.3. Full baseline metadata is in `results.json`.
- All five implementations load at the same program address in separate VMs. The upstream deterministic wallets, transaction inputs, PDA seeds, account permissions, final-state assertions, and unauthorized/overflow probes are preserved.
- Initialization is measured once per implementation. Increment is averaged over 20 calls, with `by` from 1 through 20. Failure probes are excluded from CU means.
- The adapter adds Koral, selects Counter only, and redirects output. TypeSOL is compiled afresh for the comparison. Rust/Anchor/Pinocchio comparison ELFs were built during the prior recorded benchmark setup, then executed again in this run; they were not rebuilt here.
- All five final 49-byte account states match. This does not prove equivalence across arbitrary contracts or all possible states. The extra 55-case Koral safety suite is separate from the upstream five-way benchmark.

## Reproduction

From `utility/`, prepare the upstream benchmark dependencies and Rust comparison ELFs using that repository's instructions. Build the selected TypeSOL checkout's `dist/`, then:

```sh
./gradlew test :cli:installDist
export KORAL_SBF_SDK=/path/to/solana-release/bin/platform-tools-sdk/sbf
cli/build/install/koral/bin/koral build examples/counter.kt --out build/counter
TYPESOL_REPO=/path/to/typesol-checkout \
  node benchmarks/counter.mjs /path/to/typesol-benchmarks \
  build/counter/counter.so build/comparison
```

Expose matching Agave tools on PATH for the upstream metadata collector. SDK installation/cache discovery on the TypeSOL side follows its own compiler settings. Inspect the resulting metadata; do not assume toolchain equality from an environment variable alone.

## Additional verification

`verification.json` records 8 passing compiler unit tests, 26 integration assertions, 55 Counter safety checks, 14 source-semantics cases, and a fresh source-only copy built outside the studio. The copied CLI passed its packaged VM checks and produced the same Counter ELF hash. Source mutation changes both the ELF and observed result. Unsupported calls, ordinary arithmetic, invalid entry forms, and stale successful artifacts after a failed rebuild are rejected.

The copied build uses the existing machine's JDK, Gradle caches/network, installed SDK, and Node. It proves extraction from studio paths, not offline installation or cross-platform compatibility. No DEX is compiled by this implementation yet, and none of the archived shared-TypeSOL DEX measurements should be attributed to it.

Local storage minimum in `counter-safety.json` is 899,160 lamports for 49 bytes. That is a LiteSVM storage deposit calculation, not a paid fee or live chain quote. No deployment or transaction on a public network occurred.
