# Launch MVP: Frozen Scope

Owner decision, 2026-11-11: launch a small usable compiler preview with credible benchmarks, then develop it further. TypeSOL's full feature surface is not a launch requirement.

## Included

- Independent Kotlin IR -> Koral model/checks/C backend -> public Agave SDK/Clang -> native SBF.
- Current restricted ULong-state, checked arithmetic, conditions, and multiple instructions.
- External balance references and signing-authority System transfer CPI already implemented.
- CLI check/build/doctor, preview ABI, local examples and VM tests.
- Two release examples: Counter and a single authority-funded payment instruction.
- Reproducible comparisons with independently compiled Native Rust and Pinocchio programs.

The existing broader test examples remain regressions. They are not an invitation to add more capabilities before launch. Product copy must say experimental compiler and describe the supported subset.

## Launch evidence

| Gate | Current status |
| --- | --- |
| Source compiles independently of a foreign emitter/runtime | Implemented and tested |
| CLI distribution works outside the studio | Verified on the current macOS arm64 host |
| Counter state/permissions/failures and comparison | Verified |
| Selected payment CPI path with the same signed calls and state | Verified against Native Rust and Pinocchio |
| Harness rejects deliberately incorrect Kotlin behavior | Verified: wrong paid accumulation is rejected on the first call |
| Exact source/toolchain/ELF hashes and raw measurements | Recorded alongside results |
| Supported language, setup, limits, and examples | Local documentation exists |
| Website and restricted browser preview | Deployed separately from the compiler source; not a package release |
| License and contributions | Proprietary / all rights reserved; external contributions closed |
| Public repository and release publication | Source repository is public with all rights reserved; package release remains pending |

## Comparison contract

The Counter and payment cases are separate scenarios. The payment comparison seeds an identical 57-byte state account before replaying the same signed transaction into separate VMs. It checks data, balances, permissions, failure codes, and rollback. The payment instruction's CU is measured over 20 successful calls.

Koral also includes its initializer, while the minimal Rust and Pinocchio payment fixtures implement only pay. Therefore the payment comparison does not rank whole-program binary sizes or initialization. Failure/alias cases are outside the success mean. The additional internal TypeSOL baseline has a different alias policy and some different error codes; preserve those differences rather than claiming complete equivalence.

Maintain owner/signature/writable/size/discriminator and overflow checks in every primary implementation. Do not remove checks for a more attractive CU number. These measurements concern the supplied fixtures, not a universal language or security ranking.

## Explicitly deferred

SPL Token/Token-2022, PDA-signed token transfers, arbitrary external data types/owner constraints, wider DEX arithmetic, actual DEX programs, broad Kotlin compatibility, stable Anchor IDL/client compatibility, RPC services, deployment automation, and browser execution are post-MVP work. They are not blockers for an honestly described technical preview.

## Updates after launch

Publish updates only for actual changes: new supported behavior, a working example, correctness fixes, measured improvements, or documented limits. Connect each claim to a source revision and reproducible evidence. Do not present a roadmap item as implemented. This plan does not authorize posting, repository publication, token creation, or a financial transaction.
