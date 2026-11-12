# Supported Language: Owned-State Preview

This is a restricted Kotlin implementation. The trusted API is compiled alongside source; its JVM bodies throw if called, and user JVM output is never executed. Koral checks resolved symbols and the declaration file before lowering intrinsics.

## Owned state

One optional account data class is allowed per file:

```kotlin
@Account("pool")
data class PoolState(val reserve_a: ULong, val reserve_b: ULong)

@Instruction
fun add_reserves(state: PoolState, a: ULong, b: ULong): PoolState =
    PoolState(checkedAdd(state.reserve_a, a), checkedAdd(state.reserve_b, b))
```

The class must have 1..16 immutable ULong primary-constructor fields, with no defaults, inheritance, generics, custom methods, init blocks, or nested classes. Koral supports construction, property reads, state-valued locals/conditions, and returning a new or existing state value. Other data-class methods, destructuring, and general objects are unsupported.

The seed is 1..32 lowercase ASCII characters (`a-z`, digits, `_`, `-`, starting with a letter). The type name determines the eight-byte `account:<TypeName>` SHA-256 discriminator. Only one owned state account and an implicit signing authority are currently modeled; this is not general account-constraint or arbitrary account-layout support.

The legacy no-class form, `increment(count: ULong, by: ULong): ULong`, remains supported with the Counter layout and `counter` PDA seed.

## Instructions and parameters

A file has 1..32 annotated top-level instruction functions. Names are lower_snake_case, up to 64 characters. `initialize` is reserved. The first parameter is the declared owned-state type, or `count: ULong` for the legacy form. The return type must match it.

After state, up to eight ULong data parameters and eight external account references can be interleaved. Data values and external accounts each retain their relative declaration order. Default/nullable/variadic parameters, extensions, suspend/generic functions, and helper calls are unsupported. `state` (typed form) or `count` (legacy form) is reserved internally and cannot also name another parameter.

| Source type | Meaning |
| --- | --- |
| `ULong` | Little-endian u64 instruction data |
| `AccountInfo` | External account reference, read-only contract |
| `WritableAccount` | External account requiring writable permission |

References come from the caller; programs cannot construct or cast them. `lamports(reference)` reads its current serialized lamport balance. A writable reference can be used through a read-only local. Reading account data, arbitrary owners, token-account types, public-key fields, and account equality are not implemented.

## Expressions and statements

Supported: ULong/Boolean values, initialized `val`/`var`, local assignment, `if`/`else`, explicit returns, ULong comparisons, scalar equality, short-circuit Boolean operations, and trusted intrinsics.

- `checkedAdd`, `checkedSubtract`, `checkedMultiply`: unsigned arithmetic; overflow/underflow returns custom code 5100. Ordinary wrapping Kotlin operators are rejected.
- `ensure(condition, code)`: code must be a constant ULong in 1..4294967295.
- `lamports(account)`: current external balance.
- `transferLamports(destination, amount)`: System Program transfer from the implicit signing authority. Destination must be writable.

All paths must return the state type or legacy ULong. Unsupported IR shapes fail compilation. Frontend errors include source positions; model/toolchain errors may have less precise locations. Loops, arrays, strings as runtime values, nullable values, JVM APIs, general calls, arbitrary CPI, and SPL Token operations are unsupported.

## Serialization and account ordering

Owned state is serialized explicitly: eight-byte discriminator, 32-byte authority key, N little-endian ULong fields in constructor order, and one PDA bump. Size is `41 + 8*N` bytes. This metadata is supplied by the runtime and is not part of the source class's field list. The account must be program-owned, large enough, writable, and match the discriminator and stored authority.

PDA seeds are the declared seed and authority public key, under the loaded program ID. Initialization uses the public SDK canonical PDA syscall and System Program CPI. No initialization values means zeroed fields; a complete list of ULong fields initializes them explicitly. Partial field payloads fail. Rent comes from the actual local runtime sysvar. Prefunded PDA recovery and schema migration are unsupported.

Update account order:

1. Authority, signer. Must also be writable when a transfer executes.
2. Owned state, writable.
3. Declared external accounts, in their relative source order, with declared permissions.
4. System Program after external accounts, when a transfer executes. Its exact address and executable flag are checked.

Initialization has its own fixed authority/state/System Program order. Data starts with the eight-byte `global:<instruction>` discriminator, followed by ULong arguments in relative source order. Unknown selectors and truncated values fail before state mutation. The loader decoder supports up to 32 records and assumes trusted Solana loader serialization.

## System Program CPI

`transferLamports` calls the public SDK `sol_invoke` with the System transfer instruction and existing signer privileges. It adds no PDA signature, cannot substitute an arbitrary program, and does not spend the owned state account's lamports. The System Program enforces source ownership and sufficient funds.

A conditional branch that does not execute a CPI does not itself require a System Program account or writable source. The preview ABI advertises conservative permissions for any instruction that can transfer. Callers can always provide this complete context. Writable external declarations still require writable permission regardless of branch selection.

Tests verify actual source/destination balances, current reads after CPI, duplicate references, and transaction rollback after a later arithmetic or second-CPI failure. These are local execution checks, not an audit or production payment system. See [official CPI semantics](https://solana.com/docs/core/cpi).

## Preview ABI

Successful builds emit `counter.abi.json`: state fields/offsets/size/PDA seed, instruction discriminators, data offsets, account order/permissions, and System transfer requirements. The payment tests use its discriminators to encode transactions. This is `koral-preview-abi/1`, not an Anchor IDL or stable public schema. No deployed program address or network operation is implied.
