# Accounts and System transfers

The current runtime models one owned state account with an implicit signing authority. Caller-supplied external references are separate from that state.

## Owned state layout

| Region | Byte offset |
| --- | --- |
| Account discriminator | 0..7 |
| Authority public key | 8..39 |
| ULong fields, constructor order | 40 onward |
| PDA bump | 40 + 8 × field count |

Total size is 41 + 8 × field count bytes. A single-field Counter is 49 bytes. These metadata bytes are supplied by the runtime, not fields you add to the Kotlin class.

The account discriminator is the first eight bytes of SHA-256 of account:TypeName. Each state value is encoded as little-endian u64. Treat the emitted preview ABI as the actual build's contract.

## Initialization

The runtime reserves initialize. Account order is authority (signer and writable), state (writable), System Program. The state PDA is derived from the declared seed and authority public key under the loaded program ID.

The runtime uses canonical PDA derivation and the actual rent sysvar. A declared layout can initialize all fields or default them all to zero; partial initialization payloads fail. Prefunded PDA recovery and schema migration are unsupported.

## Updating state

Update account order is authority, owned state, declared external accounts, then System Program when the instruction may transfer. External references preserve their relative order in source; ULong instruction-data parameters have their own relative order.

The runtime checks ownership, data size, discriminator, stored authority, and writable/signer permissions. Instruction data begins with an eight-byte global:instruction discriminator followed by little-endian u64 arguments.

## A payment instruction

```kotlin
@Account("payments")
data class PaymentState(val paid: ULong, val observed: ULong)

@Instruction
fun pay(state: PaymentState, recipient: WritableAccount, amount: ULong): PaymentState {
    transferLamports(recipient, amount)
    return PaymentState(checkedAdd(state.paid, amount), lamports(recipient))
}
```

This fragment belongs to the [complete payments example](../../examples/payments.kt), including its package/imports. The transfer spends the implicit signing authority's lamports. It does not spend the owned state's balance or sign as a PDA. The helper invokes only the System Program with existing signer privileges.

## Permissions and rollback

A transfer needs a writable signing authority, writable destination, and the exact executable System Program account. For a branch that does not execute a transfer, the helper's extra CPI checks are not run; declared writable external references still require writable permission. The ABI conservatively advertises permissions for any instruction that may transfer.

Owned state is stored after successful instruction execution. External transfers are actual CPIs; if later arithmetic or a second transfer fails, transaction rollback must revert both the CPI effects and state. Local tests exercise these cases. This is not arbitrary CPI, SPL Token support, a complete DEX, or a production payment system.

Implementation basis: runtime/src/counter.c, CounterC.kt, and ProgramAbi.kt.

## Fixed external account constraints

Add `@AccountOwner("<program address>")` or `@AccountAddress("<account address>")`
to an external `AccountInfo` or `WritableAccount` instruction parameter. Keys are
constant Base58 strings encoding exactly 32 bytes. The owner is the owning
program, not the wallet authority. Both constraints run before the selected
instruction body, even when an inner branch skips a transfer. They do not grant
signing privileges or validate a token-account layout.

See [the complete example](../../examples/constrained-payments.kt) and the
[language contract](../LANGUAGE.md#fixed-external-account-constraints). The example's
fixed recipient is for deterministic local tests, not a payment recommendation.
The emitted ABI includes an optional owner and the fixed address; clients should
inspect the ABI from their actual build. Unannotated references retain their
existing behavior. Mismatches return 3007 (owner) or 3011 (address) before CPI or
state writes. A later body failure still requires transaction rollback.
