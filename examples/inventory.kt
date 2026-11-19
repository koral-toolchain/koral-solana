package inventory
import dev.koral.api.*

@Account("inventory")
data class Inventory(val received: ULong, val available: ULong, val reserved: ULong, val sold: ULong)

@Instruction
fun receive(state: Inventory, quantity: ULong): Inventory {
    ensure(checkedAdd(state.available, checkedAdd(state.reserved, state.sold)) == state.received, 7100uL)
    ensure(quantity > 0uL, 7101uL)

    return Inventory(checkedAdd(state.received, quantity), checkedAdd(state.available, quantity), state.reserved, state.sold)
}

@Instruction
fun reserve(state: Inventory, quantity: ULong): Inventory {
    ensure(checkedAdd(state.available, checkedAdd(state.reserved, state.sold)) == state.received, 7100uL)
    ensure(quantity > 0uL, 7101uL)
    ensure(quantity <= state.available, 7102uL)
    return Inventory(state.received, checkedSubtract(state.available, quantity), checkedAdd(state.reserved, quantity), state.sold)
}

@Instruction
fun cancel(state: Inventory, quantity: ULong): Inventory {
    ensure(checkedAdd(state.available, checkedAdd(state.reserved, state.sold)) == state.received, 7100uL)
    ensure(quantity > 0uL, 7101uL)
    ensure(quantity <= state.reserved, 7103uL)
    return Inventory(state.received, checkedAdd(state.available, quantity), checkedSubtract(state.reserved, quantity), state.sold)
}

@Instruction
fun dispatch(state: Inventory, quantity: ULong): Inventory {
    ensure(checkedAdd(state.available, checkedAdd(state.reserved, state.sold)) == state.received, 7100uL)
    ensure(quantity > 0uL, 7101uL)
    ensure(quantity <= state.reserved, 7103uL)
    return Inventory(state.received, state.available, checkedSubtract(state.reserved, quantity), checkedAdd(state.sold, quantity))
}

@Instruction
fun restock_return(state: Inventory, quantity: ULong): Inventory {
    ensure(checkedAdd(state.available, checkedAdd(state.reserved, state.sold)) == state.received, 7100uL)
    ensure(quantity > 0uL, 7101uL)
    ensure(quantity <= state.sold, 7104uL)
    return Inventory(state.received, checkedAdd(state.available, quantity), state.reserved, checkedSubtract(state.sold, quantity))
}
