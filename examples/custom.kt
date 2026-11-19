package example
import dev.koral.api.*

@Account("receipt")
data class Receipt(val units: ULong, val paid: ULong)

// Fixed VM fixture recipient. Replace the policy and its independent tests together.
@Instruction
fun purchase(
    state: Receipt,
    @AccountAddress("GyGKxMyg1p9SsHfm15MkNUu1u9TN2JtTspcdmrtGUdse") recipient: WritableAccount,
    quantity: ULong
): Receipt {
    ensure(quantity > 0uL, 7001uL)
    ensure(quantity <= 5uL, 7002uL)
    val amount = checkedMultiply(quantity, 10_000_000uL)
    transferLamports(recipient, amount)
    return Receipt(checkedAdd(state.units, quantity), checkedAdd(state.paid, amount))
}

@Instruction
fun purchase_then_fail(state: Receipt, recipient: WritableAccount, quantity: ULong): Receipt {
    val amount = checkedMultiply(quantity, 10_000_000uL)
    transferLamports(recipient, amount)
    ensure(false, 7003uL)
    return state
}
