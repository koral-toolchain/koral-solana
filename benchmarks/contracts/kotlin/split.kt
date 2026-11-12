package example
import dev.koral.api.*
@Account("split")
data class SplitState(val sent: ULong, val observed: ULong)
@Instruction
fun distribute(
    state: SplitState, first: WritableAccount, amount_a: ULong,
    second: WritableAccount, amount_b: ULong
): SplitState {
    transferLamports(first, amount_a)
    transferLamports(second, amount_b)
    return SplitState(
        checkedAdd(state.sent, checkedAdd(amount_a, amount_b)),
        checkedAdd(lamports(first), lamports(second))
    )
}
