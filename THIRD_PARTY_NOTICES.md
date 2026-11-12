# Third-Party Notices

- Kotlin compiler, standard library, Kotlin Gradle plugin: JetBrains and contributors, Apache-2.0. They are resolved dependencies, not vendored source.
- Gradle wrapper and distribution: Gradle and contributors, Apache-2.0. The wrapper pins a verified distribution checksum.
- Agave/Solana C SDK and platform tools: external installed dependencies. Preserve their upstream licenses in redistributed toolchains; Koral does not bundle the SDK. Koral calls the public SDK interfaces.
- LLVM/Clang: external SDK tools, under their upstream licenses; not bundled here.
- `@solana/kit`: Solana contributors, MIT; `litesvm`: LiteSVM contributors, Apache-2.0. Exact versions and transitive dependencies are recorded in `testing/package-lock.json`.
- The comparative benchmark dynamically adapts the MIT-licensed TypeSOL benchmark runner. Its source repository is supplied separately by the caller; retain that repository's LICENSE. TypeSOL source/compiler are used only for the comparison, not the product compilation path.
- Archived earlier experiments retain their original notices and dependencies. They are not packaged into the current CLI.

Koral's original source is proprietary, with all rights reserved; see LICENSE.
External modification and redistribution require prior written permission.
This restriction applies to Koral's original materials, not to third-party
components governed by their own licenses. This document does not authorize
a public release or relicense third-party work.
