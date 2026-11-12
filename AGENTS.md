# Koral product maintenance

- Write code, comments, documentation and visible repository content in English.
- This repository is public. Original Koral materials are proprietary and all
  rights are reserved. Do not change visibility, license, access or publication
  settings without an explicit owner instruction. External contributions are closed.
- Follow CONTRIBUTING.md and docs/CI.md. The sole human maintainer is tidemark-labs.
  Policy checks supplement owner review; they do not replace it.
- Preserve the independent Kotlin IR -> Koral checks/C backend -> Agave SDK/LLVM
  -> native SBF path. Do not introduce a foreign compiler/runtime into product
  compilation. Benchmark adapters are comparison-only and retain their notices.
- Keep the launch scope at Counter and authority-funded System payment CPI.
  Do not add SPL Token, PDA signing or DEX support as release prerequisites.
- Compiler/runtime changes require real SBF/VM regression evidence, including
  permissions, checked arithmetic and failure rollback. Never remove safety
  checks for a more attractive compute-unit measurement.
- Run relevant unit tests and the candidate CLI outside the source checkout.
  Keep workflow gates strict: failed, cancelled, missing or skipped required jobs
  must not become successful checks. Pin handbook/action versions explicitly.
- Keep source, benchmarks and product documentation independent of the separately
  deployed website. Do not add website assets, cloud credentials, wallets, SDK
  binaries, caches or internal studio history here.
- Distinguish local tests, hosted CI, supported platforms and actual releases.
  Do not claim an audit, broad Kotlin support or production readiness.
