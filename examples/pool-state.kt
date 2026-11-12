package example
import dev.koral.api.*

@Account("pool")
data class PoolState(val reserve_a: ULong, val reserve_b: ULong)

@Instruction
fun add_reserves(state: PoolState, amount_a: ULong, amount_b: ULong): PoolState =
    PoolState(checkedAdd(state.reserve_a, amount_a), checkedAdd(state.reserve_b, amount_b))

@Instruction
fun withdraw_a(state: PoolState, amount: ULong): PoolState =
    PoolState(checkedSubtract(state.reserve_a, amount), state.reserve_b)

@Instruction
fun reset(state: PoolState): PoolState {
    val empty = PoolState(0uL, 0uL)
    return if (state.reserve_a > 0uL) empty else state
}
