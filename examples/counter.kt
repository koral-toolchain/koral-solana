package example

import dev.koral.api.Instruction
import dev.koral.api.checkedAdd

/** Uses the initial Counter ABI: the wrapper checks authority and persists the returned count. */
@Instruction
fun increment(count: ULong, by: ULong): ULong = checkedAdd(count, by)
