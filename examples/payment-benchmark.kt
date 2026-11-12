package example
import dev.koral.api.*

@Account("payments")
data class PaymentState(val paid: ULong, val observed: ULong)

@Instruction
fun pay(state: PaymentState, recipient: WritableAccount, amount: ULong): PaymentState {
    transferLamports(recipient, amount)
    return PaymentState(checkedAdd(state.paid, amount), lamports(recipient))
}
