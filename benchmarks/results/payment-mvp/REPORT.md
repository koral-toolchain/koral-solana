# Frozen MVP: Authority-Funded Payment CPI

Measured 2026-11-11. The launch scope is Counter plus a single useful System payment-CPI example, not full TypeSOL feature parity or DEX support. No compiler functionality was added for this comparison.

## Selected instruction result

| Implementation | Mean pay CU | Samples | Min/max CU |
| --- | ---: | ---: | --- |
| Native Rust, solana-program 5.1.0 | 3,187 | 20 | 3,187 / 3,187 |
| Pinocchio 0.10.2 | 1,525 | 20 | 1,525 / 1,525 |
| Koral | 1,344 | 20 | 1,344 / 1,344 |
| TypeSOL 0.43.0 additional internal baseline | 1,330 | 20 | 1,330 / 1,330 |

Koral uses about 11.9% fewer CU than Pinocchio and 57.8% fewer than this native Rust implementation in this selected fixture. These are implementation/toolchain measurements, not universal language rankings. The Rust baselines are straightforward public-SDK implementations, not a claim of maximum achievable Rust optimization.

The three primary programs are independently compiled and have different ELF hashes. They do the same pay operation: check the signer, writable state and recipient, state owner/discriminator/size and authority, verify the System Program, make an authority-funded transfer, perform checked paid accumulation, and record the recipient's current balance.

## Equivalence and negative control

The identical signed transaction is replayed in separate LiteSVM instances at the same program address. Each record contains a hash of its signed wire transaction. The 20 success cases assert exact debit/credit, paid accumulation, observed balance, all compared state bytes and lamports, and final owner. Fifteen failure cases cover permission, owner, discriminator, size, authority, missing/wrong accounts, malformed arguments, unknown instruction, insufficient funds, and overflow after CPI. A separate state/recipient alias success case is excluded from CU means.

Native Rust, Pinocchio, and Koral match across all 36 transactions, including exact failure codes and rollback. A deliberately changed Kotlin program still compiles but does not update paid; the harness rejects it on the first transaction. This guards against a benchmark that only reports CU while missing wrong behavior.

## Auxiliary differences

TypeSOL has the same outcomes and state on the standard measured payments, but rejects the owned-state/recipient alias with error 2040. It also uses different codes for some writable failures and unknown instructions. These differences are preserved in results.json; complete four-way program equivalence is not claimed. The additional baseline is internal provenance, not a product dependency or public website comparison subject.

## Scope and conditions

The harness seeds the same 57-byte PaymentState account before calling pay. Initialization is not measured. Koral includes its automatic initializer while the two minimal Rust fixtures implement pay only; therefore ELF bytes are retained for provenance but not ranked as whole-program size or deployment-cost claims.

Amounts are 1..20, one instruction per transaction, with deterministic authority/recipient/fee-payer keys, the same accounts/permissions and signing, and an independent fee payer. No blockhash checks are disabled. Failure probes and alias behavior are excluded from the success CU mean. Local storage deposits, fees, network latency, and security are different subjects.

Agave 3.1.10/platform-tools 1.52, target v0, Rust 1.89.0, LLVM/Clang 20.1.7, Kotlin 2.2.21, Node 22.22.3, LiteSVM 1.6.1. Rust release uses fat LTO, one codegen unit, and checked overflow; Koral uses its pinned -O2 C/SBF path. Cargo.lock pins dependency resolution. The extra compiler is checkout 0.43.0 at `543786e850ad426425956e497971f88103f6c987`, with the same Clang/target verified.

## Reproduction and artifacts

Run `node benchmarks/payments/build.mjs` with an installed SDK, compiler distribution, Cargo, pinned VM dependencies and optional auxiliary checkout as described in `benchmarks/payments/README.md`. A complete second build with this helper matched all CU and ELF hashes. JSON evidence includes source/runner/toolchain hashes, all outcomes, ABI, the negative control, and the second-build verification.

No live-chain deployment, public release, post, wallet funding, or financial transaction occurred. The Cargo SDK may create unfunded build keypair files under ignored build outputs; they are not packaged or published.
