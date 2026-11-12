# The supported Kotlin subset

Koral lowers explicitly supported resolved Kotlin IR. A program must use the trusted dev.koral.api declarations; similarly named functions do not become compiler intrinsics.

## Declare owned state

```kotlin
package example
import dev.koral.api.*

@Account("counter")
data class Counter(val count: ULong)

@Instruction
fun increment(state: Counter, by: ULong): Counter =
    Counter(checkedAdd(state.count, by))
```

[Download this exact example](../../examples/typed-counter.kt).

At most one account data class is allowed per source file. It has 1..16 immutable ULong primary-constructor fields. Defaults, generic parameters, inheritance, custom methods, and init blocks are unsupported. The account seed is 1..32 lowercase ASCII characters, starts with a letter, and may contain digits, underscores, and hyphens.

## Declare instructions

A file contains 1..32 annotated top-level instruction functions. Instruction names use lower_snake_case, with a maximum length of 64 characters. initialize is reserved for the runtime.

The first parameter is the owned state type, and the return value must be that state type. Up to eight ULong data arguments and eight external account references may follow. Each category keeps its relative source order. Parameters cannot be nullable, generic, variadic, or defaulted. All paths must return the expected state.

The legacy no-class form uses count: ULong as the first parameter and returns ULong. It retains the implicit Counter layout.

## Values and control flow

Supported forms include initialized val/var locals, assignments to mutable locals, ULong and Boolean expressions, field reads, state construction, comparisons, short-circuit Boolean operations, explicit returns, and if/else branches.

Loops, arrays, runtime strings, nullable values, arbitrary helper calls, destructuring, JVM APIs, and general object behavior are unsupported. Unsupported forms are rejected rather than executed on the JVM.

## Trusted operations

| Operation | Behavior |
| --- | --- |
| checkedAdd(a, b) | Checked unsigned addition |
| checkedSubtract(a, b) | Checked unsigned subtraction |
| checkedMultiply(a, b) | Checked unsigned multiplication |
| ensure(condition, code) | Fail with a constant nonzero u32 custom code |
| lamports(account) | Read a supplied external account balance |
| transferLamports(to, amount) | Transfer from the implicit authority via System CPI |

Ordinary wrapping arithmetic operators are rejected. Arithmetic overflow or underflow returns custom code 5100. ensure requires a constant ULong code in 1..4294967295.

## Account references

AccountInfo is a read-only external reference. WritableAccount requires writable permission and can be used where a read-only reference is expected. Source programs cannot construct these references. Reading arbitrary account data, token layouts, account owners, public-key fields, and account equality are not implemented.

Implementation basis: Api.kt, KoralRegistrar.kt, and ProgramChecks.kt. [Accounts and transfers](accounts.md) describes runtime responsibilities.
