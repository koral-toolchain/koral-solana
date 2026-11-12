package example

import dev.koral.api.*

@Instruction
fun increment(count: ULong, by: ULong): ULong = checkedAdd(count, by)

@Instruction
fun decrement(count: ULong, by: ULong): ULong = checkedSubtract(count, by)

@Instruction
fun set_count(count: ULong, value: ULong): ULong = value

@Instruction
fun add_scaled(count: ULong, amount: ULong, multiplier: ULong): ULong {
    ensure(multiplier > 0uL, 6002uL)
    return checkedAdd(count, checkedMultiply(amount, multiplier))
}

@Instruction
fun reset(count: ULong): ULong = 0uL
