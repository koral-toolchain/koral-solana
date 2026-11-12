# Three Additional Contract Cases

These are locally authored benchmark fixtures using the existing compiler subset. They are not copied mainnet protocols, complete DEX implementations, or additions to the frozen launch feature requirements.

| Case | Selected behavior | What it does not claim |
| --- | --- | --- |
| Quota | Authority-signed consumption up to a stored limit, with zero/overflow/limit checks | A timed or delegated rate-limit service |
| Pool | Checked updates to two stored reserve fields | Token movement, liquidity shares, swaps, or a complete pool protocol |
| Split | Two authority-funded System transfers and a state record | Token CPI, PDA vault signing, or a production payment service |

Each case has separate Kotlin, Native Rust, Pinocchio, and auxiliary TypeSOL sources. The primary Rust implementations use public SDK APIs and are independently compiled. TypeSOL is benchmark-only.

## Reproduce

From utility/, with the pinned compiler distribution and VM dependencies prepared:

```sh
./gradlew :cli:installDist
npm ci --prefix testing
export KORAL_SBF_SDK=/path/to/solana-release/bin/platform-tools-sdk/sbf
node benchmarks/contracts/build.mjs \
  --typesol-repo /path/to/typesol-checkout \
  --out build/contracts-comparison
```

Cargo must be on PATH for the Rust fixtures. Agave 3.1.10/platform-tools 1.52, target v0, solana-program 5.1.0 and Pinocchio 0.10.2 are pinned in manifests/lockfile. Rust uses release fat LTO, one codegen unit and checked overflow. The Koral compiler itself still requires no Rust build or auxiliary compiler.

The helper compiles each implementation and replays identical signed calls. The Node harness checks absolute state transitions/balances, matching primary error codes, rollback, and boundary cases. Every CU mean uses 20 successful calls; failure/alias cases are outside the mean. State is seeded identically at 57 bytes. Koral's extra initializer is not compared and whole-program ELF-size ranking is excluded.

See [the recorded comparison](../results/three-contracts/REPORT.md) for raw outcomes, source/ELF hashes, auxiliary differences, negative controls, and a second build. No deployment or real funds are involved.
