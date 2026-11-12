package example
import dev.koral.api.*

@Account("payments")
data class PaymentState(val paid: ULong, val observed: ULong)

@Instruction
fun pay(state: PaymentState, recipient: WritableAccount, amount: ULong): PaymentState {
    transferLamports(recipient, amount)
    // A later overflow must roll back the CPI as well as the owned state.
    return PaymentState(checkedAdd(state.paid, amount), lamports(recipient))
}

@Instruction
fun observe(state: PaymentState, target: AccountInfo): PaymentState =
    PaymentState(state.paid, lamports(target))

@Instruction
fun pay_if(state: PaymentState, recipient: WritableAccount, amount: ULong): PaymentState {
    if (amount > 0uL) transferLamports(recipient, amount)
    return PaymentState(checkedAdd(state.paid, amount), lamports(recipient))
}

@Instruction
fun split(
    state: PaymentState, first: WritableAccount, amount_first: ULong,
    second: WritableAccount, amount_second: ULong
): PaymentState {
    transferLamports(first, amount_first)
    transferLamports(second, amount_second)
    return PaymentState(
        checkedAdd(state.paid, checkedAdd(amount_first, amount_second)),
        checkedAdd(lamports(first), lamports(second))
    )
}

@Instruction
fun observe_writable(state: PaymentState, target: WritableAccount): PaymentState {
    val read: AccountInfo = target
    return PaymentState(state.paid, lamports(read))
}
