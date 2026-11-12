# MVP Payment Comparison

Independent Native Rust (`solana-program` 5.1.0), Pinocchio 0.10.2, and Koral implementations of the same selected `pay` behavior. The additional TypeSOL program is an internal baseline only, never a product dependency.

Koral source: `examples/payment-benchmark.kt`. It transfers SOL from a signing authority to a writable recipient, then records cumulative paid amount and the observed recipient balance in a 57-byte owned state account. An overflow after CPI must roll back the transfer. The primary Rust implementations perform the same checks and operations.

## Reproduce

From utility/, install the pinned Agave 3.1.10/platform-tools 1.52 SDK, JDK 17, Node >=22.12, and Rust/Cargo required only for the benchmark fixtures. Prepare the selected auxiliary TypeSOL checkout's dependencies and dist if using it.

```sh
./gradlew test :cli:installDist
npm ci --prefix testing
export KORAL_SBF_SDK=/path/to/solana-release/bin/platform-tools-sdk/sbf
node benchmarks/payments/build.mjs \
  --typesol-repo /path/to/typesol-checkout \
  --out build/payment-comparison
```

The helper uses the pinned Cargo.lock and SDK target v0. It never deploys or sends a live transaction. A missing auxiliary checkout is reported explicitly. Counter comparison remains a separate script and dataset.

[Recorded results](../results/payment-mvp/REPORT.md) contain measured CU, source/ELF hashes, all transactions and failures, differences in the auxiliary baseline, and the deliberate-error negative control.

The primary fixtures are locally authored benchmark code using public SDK interfaces. They are not independent security audits or exhaustive standard implementations. Rust optimization profiles and dependency versions are part of the comparison. Koral's initializer is outside this pay-only comparison, so binary-size ranking is deliberately excluded.
