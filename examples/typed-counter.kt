package example
import dev.koral.api.*

@Account("counter")
data class Counter(val count: ULong)

@Instruction
fun increment(state: Counter, by: ULong): Counter =
    Counter(checkedAdd(state.count, by))
