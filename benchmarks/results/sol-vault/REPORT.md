# SOL vault selected-update comparison

The same signed transactions execute against independent Native Rust, Pinocchio
and Koral binaries at the same program address and seeded canonical VaultState.
Each explicitly selected PDA-bump stratum has 20 deposits and 20 withdrawals,
followed by 25 failure/boundary checks (65 rows total). All three primary outcomes,
errors, states and balances match. Across four strata: 260 transaction scenarios.
Initialization is separately verified by the Koral vault suite, not measured here.

## Mean CU for each selected PDA

| Canonical bump / search attempts | Operation | Native Rust | Pinocchio | Koral | Auxiliary TypeSOL |
| --- | --- | ---: | ---: | ---: | ---: |
| 255 / 1 | deposit | 4425 | 3082 | 2878 | 2054 |
| 255 / 1 | withdraw | 3074 | 1968 | 2261 | 1073 |
| 254 / 2 | deposit | 5925 | 4582 | 4378 | 2700 |
| 254 / 2 | withdraw | 4574 | 3468 | 3761 | 1719 |
| 253 / 3 | deposit | 7425 | 6082 | 5878 | 3346 |
| 253 / 3 | withdraw | 6074 | 4968 | 5261 | 2365 |
| 250 / 6 | deposit | 11925 | 10582 | 10378 | 5284 |
| 250 / 6 | withdraw | 10574 | 9468 | 9761 | 4303 |

These are four selected deterministic fixtures, not a random distribution or a
population average. Search cost dominates the six-attempt case. Koral has lower
CU than these Rust/Pinocchio fixtures for deposit, but is above Pinocchio for
withdrawal. The auxiliary baseline has lower CU for both measured operations.
This does not establish universal compiler/language superiority. Binary byte
counts are preserved as provenance, not a whole-program size ranking: Koral also
contains an initializer and the primary fixtures implement selected updates only.

## Auxiliary scope and differences

TypeSOL0.43.0 revision543786e850ad426425956e497971f88103f6c987 was directly built
and executed using the same LLVM20.1.7 targetv0 SDK. Every measured transaction
has matching success/state/balances. However, full safety/error equivalence is
not claimed: in each stratum, the controlled nonexecutable-system VM fixture
succeeds in TypeSOL while the primary programs reject it with3008. Eleven error
representations differ per stratum, including that success/failure difference.
Raw rows retain all differences rather than silently omitting this baseline.
This constructed VM fixture is not proof of an exploitable network vulnerability.

Canonical lookup mechanisms also differ. The primary fixtures use public SDK
find-program-address syscalls. The auxiliary compiler's own runtime uses SHA256
and curve-validation syscalls, and the source requests key and bump separately.
Do not attribute the entire CU difference to Kotlin or omit stronger checks to
improve a number. No TypeSOL runtime or emitted code is used by Koral compilation.

## Reproduction and negative control

The checked-in [build helper](../../vault/build.mjs) uses locked Rust dependencies,
the same candidate CLI, and an explicitly pinned auxiliary compiler. Full source,
ELF and tool hashes are recorded in toolchain.json, koral-build.json and each
stratum's provenance/results. Native Rust uses solana-program5.1.0; Pinocchio0.10.2;
Agave3.1.10/platform-tools1.52, Rust release fatLTO/codegen-units1/overflow checks.
Both locally rebuilt copies produced identical ELF hashes, CU, signed transactions
and outcomes for all four strata. This is local reproduction, not signed provenance
or a guarantee of byte reproduction on every machine.

The [negative-control helper](../../vault/negative-control.mjs) compiles Kotlin
with the actual withdrawal removed but accounting retained. Its real SBF is
rejected at `withdraw-1: koral exact vault debit`, proving that a bookkeeping-only
program cannot pass the comparison. negative-control.json records source, compiler
and binary identities. Faulty source/binaries stay outside the repository.

The selected update cases do not establish generic PDA signing, SPL Token, arbitrary
CPI, DEX support, a security audit or production readiness. CU is compute usage,
not the storage deposit. See [the vault guide](../../../docs/VAULT.md).
