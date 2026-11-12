# Owned State, External Accounts, and System Program CPI

Executed 2026-11-11. Koral compiles declared ULong state classes and typed external references through its own Kotlin IR/model/C backend. The System transfer statement invokes the public SDK's actual CPI function. No foreign compiler or DEX runtime participates in the product build.

## Functional results

- 19 passing compiler unit tests.
- 53 top-level integration checks. Nested suites include 55 legacy Counter checks, 32 expression/argument cases, 55 typed Counter checks, 31 owned-state checks, and 37 System CPI checks. Do not add nested counts as separate disjoint totals.
- Payment calls are encoded from the emitted preview ABI. They check exact source/destination balance changes, current balance reads after CPI, account order and interleaved scalar arguments, readonly references, missing/wrong program accounts, signer and writable failures, insufficient funds, and duplicate references to owned state.
- A later arithmetic failure rolls back an already invoked transfer. A second CPI failure also rolls back the first CPI and state. System Program invocation is observed in the local VM logs.
- A copied installed distribution outside the studio builds the bundled payment example, installs its pinned Node dependencies, and passes all 37 payment checks with the same ELF hash. It uses this machine's existing JDK/SDK/Node; it does not prove offline setup or cross-platform compatibility.

The payment example is a local authority-funded transfer plus state record. It is not a vault spending its own lamports, arbitrary CPI, SPL Token implementation, audited payment product, or DEX. The 37 functional checks are not a Rust/Pinocchio CPI performance comparison; none is claimed.

## Fresh Counter baseline

The typed Counter (one declared ULong field, legacy-compatible account layout) is compared separately:

| Operation | Native Rust | Pinocchio | Koral | Anchor | TypeSOL additional internal baseline |
| --- | ---: | ---: | ---: | ---: | ---: |
| Initialize CU | 4,601 | 3,186 | 3,366 | 6,573 | 1,856 |
| Increment CU | 857 | 113 | 107 | 2,323 | 110 |

All five final 49-byte Counter states match. Initialization still trails Pinocchio. These results do not establish DEX performance or measure the payment example.

One initialization and 20 increments, deterministic wallets, matching program addresses, input/account permissions, unauthorized/overflow probes and final-state assertions from the upstream benchmark. Rust comparison ELFs were built in the earlier setup, then executed again; TypeSOL was compiled afresh as the extra internal baseline.

Agave 3.1.10/platform-tools 1.52, Clang 20.1.7, SBPF v0, LiteSVM 1.6.1, Node 22.22.3, Kotlin 2.2.21, Gradle 8.14.6. TypeSOL 0.43.0 revision `543786e850ad426425956e497971f88103f6c987`; benchmark revision `05fb4e1a56c2447becd7a017b3a8b941d2d43273`. Actual hashes and metadata are in the adjacent JSON files. Public website comparison labels remain Native Rust, Pinocchio, and Koral.

## Reproduce

From utility/:

```sh
./gradlew test :cli:installDist
npm ci --prefix testing
export KORAL_SBF_SDK=/path/to/solana-release/bin/platform-tools-sdk/sbf
node testing/verify.mjs cli/build/install/koral/bin/koral "$KORAL_SBF_SDK"
cli/build/install/koral/bin/koral build examples/payments.kt --out build/payments
node testing/payments.mjs build/payments/counter.so build/payments/test-result.json
cli/build/install/koral/bin/koral build examples/typed-counter.kt --out build/counter
TYPESOL_REPO=/path/to/typesol-checkout \
  node benchmarks/counter.mjs /path/to/typesol-benchmarks \
  build/counter/counter.so build/comparison
```

Prepare the pinned comparison sources/dependencies and Rust ELFs, and expose matching Agave tools on PATH. Inspect returned metadata rather than assuming the SDK matches. No live RPC, wallet funding, deployment, publication, or trade occurred.
