# Changelog

## Unreleased

The entries below are grouped into the 0.3.0-preview.1 candidate. The exact
verified CI archive will be promoted under the owner's four-step readiness task;
this source preparation alone is not publication.

- Add an independent aggregate-inventory consumer example and tests, recording
  both verified zero-initialized behavior and the unsupported initial-state policy.
- Refresh the post-launch roadmap and consumer evidence instead of treating the
  initial launch snapshot as the current product status.

- Preserve Node's original file/line/code frame for custom entry or imported-helper
  syntax errors, without re-executing consumer code or exposing loader frames.
  Runtime-created SyntaxErrors retain the normal compact user-source diagnostic.

- Show user test file/line locations with failed custom assertions, retaining
  assertion details without internal loader/client frames. Imported consumer
  helpers remain debuggable; unavailable source locations are labeled honestly.

- Add an Unreleased custom project with editable Kotlin and user-authored tests.
- Provide a preview ABI instruction/state client and local VM fixture helpers,
  reducing manual argument encoding, PDA derivation and account-order handling.
- Explain fixed-template ABI mismatches without leaking internal Node TypeErrors;
  retain real regression assertions rather than treating an ABI match as success.
- Verify actual custom SBF, independent price/rollback assertions, consumer input
  failures and rejection of a compiled wrong-price mutation outside the checkout.

## 0.2.0-preview.1 — 2026-11-19

[Published development prerelease](https://github.com/koral-toolchain/koral-solana/releases/tag/v0.2.0-preview.1).
The exact verified main-CI archive was promoted without rebuilding.

- Add payment and SOL-vault project templates to init, copying maintained examples
  from installed CLI resources without requiring a source checkout.
- Select the matching fixed local VM regression suite with project test settings
  or explicit --suite, retaining default Counter commands and format-1 projects.
- Invalidate default success reports during rebuilds and the selected report before
  failed/missing-build/dependency/ABI test attempts; reject protected report paths.
- Verify generated projects, real SBF behavior mutations, rollback, report freshness
  and unchanged installations on the candidate's consumer/compatibility jobs.

- Add owned-state SOL vault deposits and withdrawals, canonical PDA
  and bump checks, actual-size rent retention, authority permissions and rollback.
  Deposits use System CPI; withdrawals directly debit program-owned lamports.
- Preserve new vault statement tags in KOR7 while reading KOR4/KOR5/KOR6 models;
  older formats reject vault tags. General PDA signing is still not exposed.
- Include the executable vault example, VM safety tests and selected-update
  Native Rust/Pinocchio comparison with an executed auxiliary TypeSOL baseline.

- Add exact external account data-size and executable true/false constraints,
  enforced before the instruction body. Preserve explicit zero/false in ABI
  metadata and internal models, retaining legacy-format readers. No data decoding is added.
- Exercise metadata mismatch, aliases, unconditional validation and CPI rollback
  with the installed CLI and native LiteSVM.

- Add fixed owner and address constraints on external account parameters,
  validated at compilation and enforced before the selected instruction body.
  Preserve constraints in the preview ABI and internal model, including legacy reads.
- Verify constrained payment success, mismatches, aliases and rollback in native CI.

- Report expected source rejections without compiler-internal exception traces,
  restore diagnostic locations after nested expressions, and display readable
  unsupported literal types. Model validation reports a file-start fallback.
- Verify installed-CLI diagnostic positions and failure behavior in native CI.

## 0.1.0-dev — initial development preview

- Compile the restricted Kotlin subset through Koral's independent checks/C
  backend and the external Agave/LLVM SDK to native Solana SBF.
- Inspect generated C, the preview ABI, binary hashes and build provenance.
- Create a Counter project with init and use project-relative check/build/test.
- Diagnose the compiler/VM environment and explicitly install pinned VM tools
  into a user cache with setup.
- Verify Counter and the existing authority-funded System payment examples in
  local LiteSVM, including permissions, arithmetic failures and rollback.
- Package installation guides and unmodified upstream third-party license texts.

This is a proprietary development preview with all rights reserved. It is not a
stable language/ABI, general Kotlin support, a published Gradle plugin, a security
audit or a mainnet deployment tool. Node is required for VM testing, not for
compilation or on-chain SBF execution. JDK and the SBF SDK are external inputs.
