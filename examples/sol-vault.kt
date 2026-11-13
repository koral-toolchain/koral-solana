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

@Instruction
fun deposit_then_fail(state: VaultState, amount: ULong): VaultState {
    depositLamports(amount)
    ensure(false, 6201uL)
    return state
}

@Instruction
fun withdraw_then_fail(state: VaultState, recipient: WritableAccount, amount: ULong): VaultState {
    withdrawLamports(recipient, amount)
    ensure(false, 6202uL)
    return state
}

@Instruction
fun round_trip(state: VaultState, recipient: WritableAccount, amount: ULong): VaultState {
    depositLamports(amount)
    withdrawLamports(recipient, amount)
    return VaultState(checkedAdd(state.deposited, amount), checkedAdd(state.withdrawn, amount))
}

@Instruction
fun round_trip_then_fail(state: VaultState, recipient: WritableAccount, amount: ULong): VaultState {
    depositLamports(amount)
    withdrawLamports(recipient, amount)
    ensure(false, 6203uL)
    return state
}

@Instruction
fun withdraw_if(state: VaultState, recipient: WritableAccount, amount: ULong): VaultState {
    if (amount > 0uL) withdrawLamports(recipient, amount)
    return VaultState(state.deposited, checkedAdd(state.withdrawn, amount))
}
