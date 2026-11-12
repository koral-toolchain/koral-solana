# Counter Comparison After Multiple-Instruction Support

Measured 2026-11-11 with the current independent Kotlin compiler. The product build uses Koral's own Kotlin IR frontend/model/C backend and the public Agave C SDK; TypeSOL runs only as a separate internal baseline.

| Operation | Native Rust | Pinocchio | Koral | Anchor | TypeSOL 0.43.0 internal baseline |
| --- | ---: | ---: | ---: | ---: | ---: |
| Initialize CU | 4,601 | 3,186 | 3,362 | 6,573 | 1,856 |
| Increment CU | 857 | 113 | 107 | 2,323 | 110 |

All five final Counter states match. Koral initialization trails Pinocchio; the increment difference is small. This Counter case does not establish performance of arbitrary contracts, DEX programs, or the multi-instruction example. The public website shows Native Rust, Pinocchio, and Koral as requested by the owner; the internal TypeSOL baseline and provenance remain here.

## Conditions

The adapter executes the upstream Counter scenario unchanged: one initialization and 20 increments, matching addresses, deterministic wallets, account permissions, inputs, unauthorized/overflow probes, and final-state checks. Rust comparison ELFs were built in the earlier benchmark setup, then executed again here. TypeSOL was compiled afresh for this run.

Agave 3.1.10/platform-tools 1.52, Clang 20.1.7, SBPF v0, Node 22.22.3, LiteSVM 1.6.1, Kotlin 2.2.21, Gradle 8.14.6. Exact TypeSOL revision `543786e850ad426425956e497971f88103f6c987`; upstream benchmark revision `05fb4e1a56c2447becd7a017b3a8b941d2d43273`. Source/toolchain hashes and metadata are in `results.json`, `provenance.json`, and `koral-build.json`.

The measured single-increment Counter ELF is 7,808 bytes. The separate five-instruction example is 8,416 bytes. Both retain a 49-byte state account. The final frontend local-name fix preserves the measured Counter ELF byte for byte; verification.json records the match. Compilation support does not require importing TypeSOL code.

## Language and VM verification

Twelve compiler unit tests pass. The integration suite records 32 top-level checks, including the 55-case Counter safety suite and 32 source-semantics cases. These nested counts must not be added as if they were disjoint test totals.

New cases execute increment, decrement, set_count, add_scaled, and reset from one SBF file, with consecutive updates and rollback checks. Eight-argument decoding, a parameter whose name could collide with a compiler local, truncated data, unknown selectors, arithmetic failures, invalid signatures, and reserved names are tested. Runtime account layouts are still Counter-specific. Custom accounts and DEX support remain unimplemented.

## Reproduce

From utility/, with the pinned reference checkouts and benchmark dependencies prepared:

```sh
./gradlew test :cli:installDist
npm ci --prefix testing
export KORAL_SBF_SDK=/path/to/solana-release/bin/platform-tools-sdk/sbf
node testing/verify.mjs cli/build/install/koral/bin/koral "$KORAL_SBF_SDK"
cli/build/install/koral/bin/koral build examples/counter.kt --out build/counter
TYPESOL_REPO=/path/to/typesol-checkout \
  node benchmarks/counter.mjs /path/to/typesol-benchmarks \
  build/counter/counter.so build/comparison
```

Expose matching Agave tools on PATH and verify the resulting toolchain metadata. See the earlier `gradle-counter/REPORT.md` for baseline setup limitations. No live deployment, RPC transaction, trade, or publication occurred.
