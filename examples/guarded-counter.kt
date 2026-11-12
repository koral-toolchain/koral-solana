package example

import dev.koral.api.Instruction
import dev.koral.api.checkedAdd
import dev.koral.api.ensure

@Instruction
fun increment(count: ULong, by: ULong): ULong {
    ensure(by > 0uL, 6001uL)
    val next = checkedAdd(count, by)
    if (next > 1_000_000uL) return count
    return next
}
