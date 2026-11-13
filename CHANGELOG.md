# Changelog

## Unreleased

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
