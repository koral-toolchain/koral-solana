package example
import dev.koral.api.*

@Account("payments")
data class PaymentState(val paid: ULong, val observed: ULong)

// A fixed demonstration destination, not a recommended payment address.
@Instruction
fun pay(
    state: PaymentState,
    @AccountOwner("11111111111111111111111111111111")
    @AccountAddress("GyGKxMyg1p9SsHfm15MkNUu1u9TN2JtTspcdmrtGUdse")
    @AccountDataSize(0)
    @AccountExecutable(false)
    recipient: WritableAccount,
    amount: ULong
): PaymentState {
    transferLamports(recipient, amount)
    return PaymentState(checkedAdd(state.paid, amount), lamports(recipient))
}

@Instruction
fun observe_owner(
    state: PaymentState,
    @AccountOwner("11111111111111111111111111111111") target: AccountInfo
): PaymentState = PaymentState(state.paid, lamports(target))

@Instruction
fun observe_address(
    state: PaymentState,
    @AccountAddress("GyGKxMyg1p9SsHfm15MkNUu1u9TN2JtTspcdmrtGUdse") target: AccountInfo
): PaymentState = PaymentState(state.paid, lamports(target))

@Instruction
fun observe_program_owned(
    state: PaymentState,
    @AccountOwner("EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN") target: AccountInfo
): PaymentState = PaymentState(state.paid, lamports(target))

@Instruction
fun pair(
    state: PaymentState,
    @AccountAddress("GyGKxMyg1p9SsHfm15MkNUu1u9TN2JtTspcdmrtGUdse") first: AccountInfo,
    @AccountOwner("11111111111111111111111111111111") second: AccountInfo
): PaymentState = PaymentState(state.paid, checkedAdd(lamports(first), lamports(second)))

@Instruction
fun pay_if(
    state: PaymentState,
    @AccountOwner("11111111111111111111111111111111")
    @AccountAddress("GyGKxMyg1p9SsHfm15MkNUu1u9TN2JtTspcdmrtGUdse")
    @AccountDataSize(0)
    @AccountExecutable(false)
    recipient: WritableAccount,
    amount: ULong
): PaymentState {
    if (amount > 0uL) transferLamports(recipient, amount)
    return PaymentState(checkedAdd(state.paid, amount), lamports(recipient))
}

@Instruction
fun pay_then_fail(
    state: PaymentState,
    @AccountOwner("11111111111111111111111111111111")
    @AccountAddress("GyGKxMyg1p9SsHfm15MkNUu1u9TN2JtTspcdmrtGUdse")
    @AccountDataSize(0)
    @AccountExecutable(false)
    recipient: WritableAccount,
    amount: ULong
): PaymentState {
    transferLamports(recipient, amount)
    ensure(false, 6200uL)
    return state
}

@Instruction
fun observe_data(
    state: PaymentState,
    @AccountOwner("EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN")
    @AccountDataSize(8) @AccountExecutable(false) target: AccountInfo
): PaymentState = PaymentState(state.paid, lamports(target))

@Instruction
fun observe_empty(
    state: PaymentState,
    @AccountOwner("11111111111111111111111111111111")
    @AccountDataSize(0) @AccountExecutable(false) target: AccountInfo
): PaymentState = PaymentState(state.paid, lamports(target))

@Instruction
fun observe_program(state: PaymentState, @AccountExecutable(true) target: AccountInfo): PaymentState =
    PaymentState(state.paid, lamports(target))

@Instruction
fun observe_non_program(state: PaymentState, @AccountExecutable(false) target: AccountInfo): PaymentState =
    PaymentState(state.paid, lamports(target))

@Instruction
fun observe_state(
    state: PaymentState,
    @AccountOwner("EqGyjYr2RHtEV5zQXCPpZ8neqU9tMgmruvuJRW4Pz2aN")
    @AccountDataSize(57) @AccountExecutable(false) target: AccountInfo
): PaymentState = PaymentState(state.paid, lamports(target))

@Instruction
fun metadata_pair(
    state: PaymentState, @AccountDataSize(8) first: AccountInfo,
    @AccountDataSize(0) second: AccountInfo
): PaymentState = PaymentState(state.paid, checkedAdd(lamports(first), lamports(second)))

@Instruction
fun observe_any(state: PaymentState, target: AccountInfo): PaymentState =
    PaymentState(state.paid, lamports(target))
