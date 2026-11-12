package example
import dev.koral.api.*
@Account("pool")
data class Pool(val reserve_a: ULong, val reserve_b: ULong)
@Instruction
fun add_reserves(state: Pool, amount_a: ULong, amount_b: ULong): Pool =
    Pool(checkedAdd(state.reserve_a, amount_a), checkedAdd(state.reserve_b, amount_b))
