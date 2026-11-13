package dev.koral.compiler.model

enum class ValueType { U64, BOOL, STATE, ACCOUNT_READONLY, ACCOUNT_WRITABLE }
enum class Operation { ADD, SUBTRACT, MULTIPLY, EQ, NE, LT, LE, GT, GE, AND, OR }
sealed interface Expression {
    data class Number(val value: ULong) : Expression
    data class BooleanValue(val value: Boolean) : Expression
    data class Variable(val name: String) : Expression
    data class Binary(val operation: Operation, val left: Expression, val right: Expression) : Expression
    data class Choice(val condition: Expression, val yes: Expression, val no: Expression) : Expression
    data class Not(val value: Expression) : Expression
    data class Construct(val values: List<Expression>) : Expression
    data class Field(val value: Expression, val name: String) : Expression
    data class AccountLamports(val account: Expression) : Expression
}
sealed interface Statement {
    data class Bind(val name: String, val type: ValueType, val mutable: Boolean, val value: Expression) : Statement
    data class Assign(val name: String, val value: Expression) : Statement
    data class Return(val value: Expression) : Statement
    data class Branch(val condition: Expression, val yes: List<Statement>, val no: List<Statement>) : Statement
    data class Require(val condition: Expression, val code: UInt) : Statement
    data class TransferLamports(val to: Expression, val amount: Expression) : Statement
}
/** Counter state is implicit; arguments are instruction-data ULong values in declaration order. */
data class AccountParameter(val name: String, val writable: Boolean, val owner: String? = null, val address: String? = null, val dataSize: Int? = null, val executable: Boolean? = null)
data class CounterInstruction(val name: String, val arguments: List<String>, val body: List<Statement>, val accounts: List<AccountParameter> = emptyList())
data class StateLayout(val name: String, val seed: String, val fields: List<String>)
data class CounterProgram(val source: String, val instructions: List<CounterInstruction>, val account: StateLayout? = null)
