package example
import dev.koral.api.*
@Account("quota")
data class Quota(val used: ULong, val limit: ULong)
@Instruction
fun consume(state: Quota, amount: ULong): Quota {
    ensure(amount > 0uL, 6001uL)
    val next = checkedAdd(state.used, amount)
    ensure(next <= state.limit, 6002uL)
    return Quota(next, state.limit)
}
