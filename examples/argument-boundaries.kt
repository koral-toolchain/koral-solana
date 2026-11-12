package example
import dev.koral.api.*

@Instruction
fun weighted_add(
    count: ULong, local_0: ULong, a: ULong, b: ULong, c: ULong,
    d: ULong, e: ULong, f: ULong, g: ULong
): ULong {
    val delta = checkedAdd(local_0, g)
    return checkedAdd(count, delta)
}
