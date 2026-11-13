# Selected SOL vault comparison

Independently authored Native Rust (solana-program5.1.0), Pinocchio0.10.2 and Koral
implementations of the selected deposit/withdraw behavior. TypeSOL0.43.0 is an
executed auxiliary baseline, never a product compilation dependency.

The same signed transactions execute against separate VMs at the same program
address and canonical 57-byte VaultState. Primary implementations check matching
permissions, owner/discriminator/authority, canonical key/bump, actual rent floor,
self-alias and arithmetic failure conditions. Deposits use System CPI; withdrawals
directly debit program-owned lamports. Initialization is excluded from this
comparison; the separate Koral vault suite verifies actual initialization.

Four deterministic authority fixtures select canonical bumps250/255/254/253
(6/1/2/3 search attempts). Each repeats20 deposits and20 withdrawals. These are
explicitly selected strata, not a random population, mixed average or universal
language/framework ranking. Full-program ELF size is not ranked.

## Reproduce

Prepare the pinned SDK3.1.10/platform-tools1.52, JDK17, Node22.12+, Cargo for the
benchmark fixtures, and a clean built TypeSOL0.43.0 checkout at
543786e850ad426425956e497971f88103f6c987. The latter must have dist/compiler built.

```sh
./gradlew --no-daemon test :cli:installDist
npm ci --ignore-scripts --prefix testing
node benchmarks/vault/build.mjs --sdk /path/to/sbf-sdk --typesol-repo /path/to/typesol --out build/vault-comparison
```

The helper requires the additional baseline, uses Cargo.lock and targetv0,
checks LLVM/target agreement, and records actual tools, sources, ELFs, transactions,
final snapshots and errors. An optional --cli points at an extracted candidate.
No benchmark Rust dependency is required to install or compile with Koral.

TypeSOL uses its own SHA256/curve-syscall canonical lookup, while the three primary
programs use public SDK find-program-address syscalls. Its source requests key
and bump through separate public builtins. Preserve actual outcomes and custom
error differences; this is not a claim of identical instruction sequences or
all-input compiler equivalence. No foreign runtime/code is linked into Koral.

[Recorded results](../results/sol-vault/REPORT.md) include limitations and a
negative control: an actually compiled wrong withdrawal must fail exact balance
checks. The compiler/runtime and original source rights remain governed by the
product LICENSE; dependencies retain their own terms.

To reproduce the deliberate incorrect-withdrawal rejection after the build:

```sh
node benchmarks/vault/negative-control.mjs --sdk /path/to/sbf-sdk --comparison-out build/vault-comparison
```

Use the same optional --cli for both helpers when testing an extracted candidate.
