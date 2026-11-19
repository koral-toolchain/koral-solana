# Testing and errors

Use the test that matches the program. The 0.2.0-preview.1 CLI selects the fixed counter/payments/sol-vault suite from the project or an explicit --suite option. The older v0.1.0-dev archive remains Counter-only.

## Compiler tests

From the utility source checkout:

```sh
./gradlew test :cli:installDist
```

Compiler tests check model serialization, validation, checked operations, control flow, and related compiler behavior. They do not replace native execution checks.

## Counter verification

```sh
npm ci --prefix cli/build/install/koral/testing
cli/build/install/koral/bin/koral test build/counter/counter.so
```

Build the typed Counter first. The verifier exercises the native artifact in local LiteSVM with deterministic test signers. No mainnet transaction or wallet connection is involved.

## Payment verification

```sh
cli/build/install/koral/bin/koral build examples/payments.kt --out build/payments
cli/build/install/koral/bin/koral setup
cli/build/install/koral/bin/koral test build/payments/counter.so --suite payments
```

Use the payments suite for this exact contract. A generated payments project selects it automatically with `koral test`. The payment checks inspect actual local balances, reads after CPI, permissions, duplicate references, and rollback on failure.

## SOL vault verification

Generate `koral init hello-vault --template sol-vault`, enter that project, then run
setup/check/build/test. The selected vault suite verifies canonical PDA/permissions,
actual-size rent retention and CPI/direct-withdrawal rollback. Explicit binaries
need `--suite sol-vault` and their adjacent generated counter.abi.json.

The template suites assert their declared contracts. If you change instructions,
state layout, seed or behavior, supply independent VM tests for that contract.
Default test reports are removed before a rebuild; a failed test invalidates its
selected report even when dependencies or ABI are missing. This is not an audit.

## Broader integration checks

```sh
npm ci --prefix testing
node testing/verify.mjs cli/build/install/koral/bin/koral "$KORAL_SBF_SDK"
```

This source-checkout harness builds multiple programs and checks source changes, expected rejections, failed-rebuild artifacts, and local behavior. Benchmark reproduction is separate; recorded CU is not a general throughput or security claim.

## Common failures

| Message or result | What to inspect |
| --- | --- |
| Set KORAL_SBF_SDK or pass --sdk | Local SDK environment or explicit build option |
| Invalid SDK | c/sbf.ld and dependencies/platform-tools/llvm/bin/clang |
| Missing compiler plugin JAR | Use the installed CLI distribution, not an ad hoc class directory |
| Unsupported call | Use supported trusted operations; ordinary wrapping arithmetic is rejected |
| Every instruction path must return | Return the declared state type on every branch |
| Missing test runner | Preserve the installed testing directory and install its Node dependencies |
| Tool timed out | The compiler/native tool step exceeded its bounded timeout |

## Selected runtime error codes

| Code | Meaning in the current Koral runtime |
| --- | --- |
| 102 | Invalid instruction or serialized input data |
| 2006 | Initialization PDA does not match the canonical address |
| 2040 | Initialization authority/state or withdrawal state/recipient alias |
| 3002 | State discriminator mismatch |
| 3003 | State data too short |
| 3005 | Required accounts missing |
| 3006 | Required writable permission missing |
| 3007 | State owner or declared external owner mismatch |
| 3008 | Invalid System Program address or executable flag |
| 3010 | Required signer missing |
| 3011 | Declared external account address mismatch |
| 3012 | Declared exact external data size mismatch |
| 3013 | Declared external executable flag mismatch |
| 3014 | Insufficient owned-vault funds after retaining rent |
| 5100 | Checked arithmetic overflow or underflow |
| 6000 | Stored state authority mismatch |

System Program errors may also propagate from CPI; this table is not a complete Solana error catalog or a stable public error specification. A custom ensure code may reuse a numeric value, so inspect the actual source too. Passing local checks is not an audit.

Implementation basis: testing/, CompilerTest.kt, cli/Main.kt, and runtime/src/counter.c.
