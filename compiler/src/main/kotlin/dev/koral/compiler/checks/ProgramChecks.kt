package dev.koral.compiler.checks

import dev.koral.compiler.model.*

object ProgramChecks {
    data class Binding(val type: ValueType, val mutable: Boolean)
    fun validate(program: CounterProgram) {
        val layout=program.account
        if(layout!=null) {
            require(layout.name.matches(Regex("[A-Z][A-Za-z0-9]{0,63}"))) { "Invalid account type name" }
            require(layout.seed.matches(Regex("[a-z][a-z0-9_-]{0,31}"))) { "Account seed must be 1..32 lowercase ASCII characters" }
            require(layout.fields.size in 1..16 && layout.fields.distinct().size==layout.fields.size) { "Expected 1..16 distinct ULong fields" }
            require(layout.fields.all { it.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")) }) { "Invalid state field name" }
        }
        require(program.instructions.size in 1..32) { "Expected 1..32 instructions" }
        require(program.instructions.map { it.name }.distinct().size==program.instructions.size) { "Duplicate instruction name" }
        for(instruction in program.instructions) {
            require(instruction.name.matches(Regex("[a-z][a-z0-9_]{0,63}")) && instruction.name!="initialize") { "Instruction names must be lower_snake_case; initialize is reserved" }
            require(instruction.arguments.size<=8 && instruction.arguments.distinct().size==instruction.arguments.size) { "Expected up to eight distinct arguments" }
            val stateName=if(layout==null)"count" else "state"
            require(instruction.accounts.size<=8) { "Expected up to eight external accounts" }
            val allNames=instruction.arguments+instruction.accounts.map { it.name }
            require(allNames.distinct().size==allNames.size) { "Duplicate parameter name" }
            require(allNames.all { it!=stateName && it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) }) { "Invalid argument name" }
            val bindings=instruction.arguments.associateWith { Binding(ValueType.U64,false) }.toMutableMap()
            instruction.accounts.forEach { bindings[it.name]=Binding(if(it.writable)ValueType.ACCOUNT_WRITABLE else ValueType.ACCOUNT_READONLY,false) }
            bindings[stateName]=Binding(if(layout==null)ValueType.U64 else ValueType.STATE,false)
            require(block(instruction.body,bindings,layout)) { "Every instruction path must return ${layout?.name ?: "a ULong"}" }
        }
    }
    fun type(e: Expression, env: Map<String,Binding>, layout: StateLayout?): ValueType = when(e) {
        is Expression.AccountLamports -> {
            require(type(e.account,env,layout) in setOf(ValueType.ACCOUNT_READONLY,ValueType.ACCOUNT_WRITABLE)) { "lamports requires an external account" };ValueType.U64
        }
        is Expression.Number -> ValueType.U64
        is Expression.BooleanValue -> ValueType.BOOL
        is Expression.Variable -> env[e.name]?.type ?: error("Unknown value ${e.name}")
        is Expression.Construct -> {
            require(layout!=null && e.values.size==layout.fields.size) { "Account constructor fields do not match" }
            require(e.values.all { type(it,env,layout)==ValueType.U64 });ValueType.STATE
        }
        is Expression.Field -> {
            require(layout!=null && e.name in layout.fields) { "Unknown state field ${e.name}" }
            require(type(e.value,env,layout)==ValueType.STATE);ValueType.U64
        }
        is Expression.Not -> { require(type(e.value,env,layout)==ValueType.BOOL); ValueType.BOOL }
        is Expression.Choice -> {
            require(type(e.condition,env,layout)==ValueType.BOOL)
            val yes=type(e.yes,env,layout);val no=type(e.no,env,layout)
            if(yes in setOf(ValueType.ACCOUNT_READONLY,ValueType.ACCOUNT_WRITABLE) && no in setOf(ValueType.ACCOUNT_READONLY,ValueType.ACCOUNT_WRITABLE)) {
                if(yes==no)yes else ValueType.ACCOUNT_READONLY
            } else { require(yes==no) { "Conditional result types differ" };yes }
        }
        is Expression.Binary -> {
            val left=type(e.left,env,layout);val right=type(e.right,env,layout);require(left==right) { "Operand types differ" }
            when(e.operation) {
                Operation.AND,Operation.OR -> { require(left==ValueType.BOOL);ValueType.BOOL }
                Operation.ADD,Operation.SUBTRACT,Operation.MULTIPLY -> { require(left==ValueType.U64);ValueType.U64 }
                Operation.EQ,Operation.NE -> { require(left in setOf(ValueType.U64,ValueType.BOOL)) { "Account equality is unsupported" };ValueType.BOOL }
                else -> { require(left==ValueType.U64);ValueType.BOOL }
            }
        }
    }
    private fun assignable(expected: ValueType, actual: ValueType)=expected==actual || (expected==ValueType.ACCOUNT_READONLY && actual==ValueType.ACCOUNT_WRITABLE)
    private fun block(statements: List<Statement>, env: MutableMap<String,Binding>, layout: StateLayout?): Boolean {
        var returns=false
        for(s in statements) {
            require(!returns) { "Unreachable statement" }
            when(s) {
                is Statement.Bind -> {
                    require(s.name !in env) { "Duplicate local ${s.name}" }
                    require(assignable(s.type,type(s.value,env,layout)));env[s.name]=Binding(s.type,s.mutable)
                }
                is Statement.Assign -> {
                    val old=env[s.name]?:error("Unknown local ${s.name}")
                    require(old.mutable) { "Cannot assign to ${s.name}" };require(assignable(old.type,type(s.value,env,layout)))
                }
                is Statement.TransferLamports -> {
                    require(type(s.to,env,layout)==ValueType.ACCOUNT_WRITABLE) { "Transfer destination must be writable" }
                    require(type(s.amount,env,layout)==ValueType.U64)
                }
                is Statement.Return -> { require(type(s.value,env,layout)==if(layout==null)ValueType.U64 else ValueType.STATE);returns=true }
                is Statement.Require -> { require(type(s.condition,env,layout)==ValueType.BOOL);require(s.code!=0u) { "Error code must be nonzero" } }
                is Statement.Branch -> {
                    require(type(s.condition,env,layout)==ValueType.BOOL)
                    val yes=block(s.yes,env.toMutableMap(),layout);val no=block(s.no,env.toMutableMap(),layout);returns=yes&&no
                }
            }
        }
        return returns
    }
}
