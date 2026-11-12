package example
import dev.koral.api.*

@Instruction
fun increment(count: ULong, by: ULong): ULong {
    ensure(by > 0uL && by <= 100uL, 6001uL)
    var result = checkedMultiply(count, by)
    if (result > 100uL) {
        result = checkedSubtract(result, 1uL)
    } else {
        result = checkedAdd(result, 1uL)
    }
    return result
}
