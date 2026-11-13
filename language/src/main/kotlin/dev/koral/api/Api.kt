package dev.koral.api

/** Marks a Counter state update instruction; the first parameter is the current count. */
@Target(AnnotationTarget.FUNCTION)
annotation class Instruction

/** Checked arithmetic is explicit; ordinary Kotlin unsigned wrapping operators are not substituted. */
fun checkedAdd(left: ULong, right: ULong): ULong = error("Compile with Koral")
fun checkedSubtract(left: ULong, right: ULong): ULong = error("Compile with Koral")
fun checkedMultiply(left: ULong, right: ULong): ULong = error("Compile with Koral")
fun ensure(condition: Boolean, errorCode: ULong): Unit = error("Compile with Koral")

/** Declares a single owned state layout; fields are immutable ULong values in constructor order. */
@Target(AnnotationTarget.CLASS)
annotation class Account(val seed: String)

/** External account references are supplied by the caller, never constructed by source programs. */
open class AccountInfo protected constructor()
class WritableAccount private constructor() : AccountInfo()

fun lamports(account: AccountInfo): ULong = error("Compile with Koral")
/** Transfers from the implicit signing authority through the System Program. */
fun transferLamports(to: WritableAccount, amount: ULong): Unit = error("Compile with Koral")

/** Requires an external account to be owned by this fixed program address. */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class AccountOwner(val address: String)

/** Requires an external account to have this fixed address. */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class AccountAddress(val address: String)

/** Requires an external account to have exactly this many data bytes. */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class AccountDataSize(val bytes: Int)

/** Requires the external account's executable flag to equal this value. */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class AccountExecutable(val value: Boolean)
