# Koral documentation

Write a small Solana program in a restricted Kotlin subset. Inspect the generated C and native SBF artifact, then verify behavior in a local VM.

Koral is an experimental compiler preview. The JVM hosts the compiler; it is not part of the resulting Solana program. General Kotlin libraries, arbitrary CPI, SPL Token, and full DEX programs are outside the current supported scope.

## Choose your next step

- [Get started](quickstart.md): build the CLI and run a typed Counter.
- [Language guide](language.md): declarations, supported values, and checked operations.
- [Accounts and transfers](accounts.md): owned state, account ordering, and authority-funded System CPI.
- [CLI and artifacts](cli.md): exact commands and emitted files.
- [Testing and errors](testing.md): local verification and common failures.
- [Compiler architecture](architecture.md): follow source through the actual implementation.
- [Browser preview](browser.md): the separate allowlisted-example adapter.

## The compilation path

```text
Kotlin source
  -> resolved Kotlin IR
  -> Koral model and validation
  -> generated C and runtime
  -> Clang/LLVM + Agave C SDK
  -> native Solana SBF (.so)
```

## Start with the supported examples

[Typed Counter](../../examples/typed-counter.kt), [owned state](../../examples/pool-state.kt), and [System payments](../../examples/payments.kt) are exact source snapshots from the utility examples. They are downloads, not a simulated compiler.

The launch scope stays narrow: Counter and an authority-funded System payment example. Local test results do not establish an audit or production readiness. Public source release, deployment, and hosted service availability are separate from this documentation.
