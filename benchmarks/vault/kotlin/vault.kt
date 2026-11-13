package example
import dev.koral.api.*

@Account("vault")
data class VaultState(val deposited: ULong, val withdrawn: ULong)

@Instruction
fun deposit(state: VaultState, amount: ULong): VaultState {
    ensure(amount > 0uL, 6100uL)
    depositLamports(amount)
    return VaultState(checkedAdd(state.deposited, amount), state.withdrawn)
}

@Instruction
fun withdraw(state: VaultState, recipient: WritableAccount, amount: ULong): VaultState {
    ensure(amount > 0uL, 6100uL)
    withdrawLamports(recipient, amount)
    return VaultState(state.deposited, checkedAdd(state.withdrawn, amount))
}

