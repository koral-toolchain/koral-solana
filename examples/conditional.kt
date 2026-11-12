package example
import dev.koral.api.*

@Instruction
fun increment(count: ULong, by: ULong): ULong {
    ensure(by == 0uL || by < 100uL, 6002uL)
    return if (by == 0uL) count else checkedSubtract(count, by)
}
