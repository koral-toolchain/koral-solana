# Three Additional Contract Comparisons

Measured 2026-11-11 using the existing Koral compiler. These are selected, locally authored contract operations: quota consumption, reserve accounting, and two-recipient SOL transfer. They are not complete imported mainnet protocols or DEX ports. The frozen launch MVP remains Counter plus a single payment example.

## Success-path compute units

| Selected instruction | Native Rust | Pinocchio | Koral | Additional internal TypeSOL baseline |
| --- | ---: | ---: | ---: | ---: |
| Quota consume | 866 | 140 | 112 | 111 |
| Add two reserve fields | 870 | 144 | 117 | 116 |
| Distribute SOL to two recipients | 5,300 | 2,936 | 2,599 | 2,537 |

Each mean is 20 successful calls; min/max for each implementation are the same as its mean in these fixtures. These numbers concern these implementations and inputs, not universal language/compiler rankings. Native uses solana-program 5.1.0; Pinocchio is 0.10.2. This does not claim either Rust fixture is the most optimized possible implementation.

## Behavior checked

- Quota: 33 transactions, including zero amount, above-limit rejection, overflow and exact-limit acceptance.
- Pool: 32 transactions, including first/second-field overflow, all-state rollback, and a zero update. This is accounting only; it moves no tokens and implements no swap.
- Split: 37 transactions, including readonly/missing/wrong contexts, later-CPI failure, two-CPI execution followed by arithmetic failure, and aliased recipients. Rollback covers all source/destination balances and state.

Across the 102 test transactions, Native Rust, Pinocchio and Koral match state bytes/owner/lamports, observed wallet balances, success/failure and exact error codes. The very same signed transaction is replayed in each VM; its wire hash is recorded per row. Successful measured cases also assert the intended absolute state and balance transition rather than relying only on mutual agreement. No blockhash checking is disabled.

For each case, a deliberate Kotlin bug still compiles but is rejected on the first call: not updating used, not updating the second reserve, or omitting the second transfer. The actual mutations and failure results are in negative-controls.json.

## Auxiliary baseline differences

Quota and Pool have no auxiliary outcome/state mismatch in the tested cases, but some failure codes differ. Split's aliased-recipient call is rejected by TypeSOL with its alias policy while the three primary programs accept the defined operation. Keep this behavior visible; complete four-way program equivalence is not claimed. The auxiliary source/compiler never participates in the Koral build.

## Conditions and reproduction

Every case starts from the same manually seeded 57-byte owned-state layout for that case. Only the named update instruction is measured. Koral includes its automatic initializer; the minimal Rust fixtures do not. Initialization, total ELF size, program deployment cost and whole-protocol compatibility are deliberately not ranked.

Agave 3.1.10/platform-tools 1.52, target v0, Rust 1.89.0, LLVM/Clang 20.1.7, Kotlin 2.2.21, Node 22.22.3, LiteSVM 1.6.1. Rust release: fat LTO, one codegen unit, overflow checks. Cargo.lock pins resolution. The extra baseline is TypeSOL 0.43.0 revision `543786e850ad426425956e497971f88103f6c987`, verified against the same Clang/target.

`benchmarks/contracts/build.mjs` rebuilt all six Rust/Pinocchio binaries and three Koral/auxiliary programs in a second output directory. Every CU mean and ELF hash matched. Source/runner hashes, full transactions, failure results, compiler manifests, preview ABI and toolchain records are stored alongside this report. These are same-host reproductions, not an offline or cross-platform guarantee.

The benchmark sources and exact commands are in benchmarks/contracts/README.md. No compiler feature was added for these cases. No publication, deployment, RPC funding, token operation or live transaction occurred.
