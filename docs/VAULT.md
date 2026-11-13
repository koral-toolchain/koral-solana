# Minimal SOL vault

Status: unreleased, source builds only. The existing v0.1.0-dev archive and website
worker do not include this feature. Use [examples/sol-vault.kt](../examples/sol-vault.kt).

## Behavior

The vault is the existing program-owned state PDA at `["vault", authority key]`.
It contains the VaultState discriminator, authority, deposited/withdrawn totals
and canonical bump (57 bytes). Initialization uses the existing canonical PDA
creation path and the current rent sysvar.

`deposit` requires the authority signature and writable authority/state, then
uses an actual System Program transfer. `withdraw` requires the stored authority
signature, writable state/recipient and canonical key/bump. It debits the owned
state directly and retains the storage minimum for the actual account data size.
It needs no System Program account or extra PDA signing privilege. The recipient
can be the authority, but cannot be the vault itself.

The example rejects zero amounts with code6100; this is source policy rather than
an intrinsic restriction. Totals record calls through these example operations,
not all unsolicited incoming SOL. Spendable funds are actual balance minus rent
minimum. No data resizing, generic PDA signing, token operations or close method
is introduced. [Solana ownership rules](https://solana.com/docs/core/accounts).

## Build and verify locally

From the product checkout, with the documented JDK/SDK and Node requirements:

```sh
./gradlew --no-daemon test :cli:installDist
cli/build/install/koral/bin/koral build examples/sol-vault.kt --out build/vault --sdk /path/to/sbf-sdk
npm ci --ignore-scripts --prefix testing
node testing/vault.mjs build/vault/counter.so build/vault/tests.json
```

The generic `koral test` command remains Counter-only. The vault runner loads
this example's ABI and tests initialization, exact balances, canonical identity,
authority/permission failures, rent retention, aliases, overflow and later failure
rollback, including a deposit followed by a withdrawal in one instruction.
All funds and signers are deterministic local VM fixtures; no RPC is used.

The preview ABI retains authority/state/external ordering. Deposit uses the
System Program after external accounts; withdrawal alone does not. Optional
vaultDeposit/vaultWithdrawal flags identify operations. Existing account
constraints can be combined with recipient declarations as needed.

## Comparison evidence

The separate [benchmark](../benchmarks/vault/README.md) compares selected deposit
and withdrawal updates on seeded state against independent Native Rust and
Pinocchio programs, with a directly executed TypeSOL baseline. Initialization
costs and full-program ELF-size rankings are excluded. PDA search attempts affect
CU, so results are reported per selected canonical bump rather than as a universal
language performance claim. The deliberately incorrect withdrawal build must fail
the exact balance checks. Broader security review and production support are not
established by these local checks.
