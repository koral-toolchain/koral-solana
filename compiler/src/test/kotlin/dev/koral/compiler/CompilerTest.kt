package dev.koral.compiler

import dev.koral.compiler.model.*
import dev.koral.compiler.checks.ProgramChecks
import dev.koral.compiler.backend.c.CounterC
import java.nio.file.Files
import kotlin.test.*

class CompilerTest {
    private val count=Expression.Variable("count")
    private fun program(vararg statements:Statement)=CounterProgram("test.kt",listOf(CounterInstruction("increment",listOf("by"),statements.toList())))
    @Test fun codecRoundTripPreservesUnsignedAndBranches() {
        val source=program(Statement.Branch(Expression.BooleanValue(true),listOf(Statement.Return(Expression.Number(ULong.MAX_VALUE))),listOf(Statement.Return(count))))
        val file=Files.createTempFile("koral-codec-",".ir")
        try { ProgramCodec.write(source,file);assertEquals(source,ProgramCodec.read(file)) } finally { Files.delete(file) }
    }
    @Test fun rejectsTruncatedOrTrailingModel() {
        val file=Files.createTempFile("koral-codec-",".ir")
        try {
            Files.write(file,byteArrayOf(1,2));assertFails { ProgramCodec.read(file) }
            ProgramCodec.write(program(Statement.Return(count)),file)
            Files.write(file,Files.readAllBytes(file)+byteArrayOf(0));assertFails { ProgramCodec.read(file) }
        } finally { Files.delete(file) }
    }
    @Test fun requiresReturnOnEveryPath() {
        assertFails { ProgramChecks.validate(program(Statement.Branch(Expression.BooleanValue(true),listOf(Statement.Return(count)),emptyList()))) }
    }
    @Test fun rejectsAssignmentToInstructionParameter() {
        assertFails { ProgramChecks.validate(program(Statement.Assign("count",Expression.Number(4u)),Statement.Return(count))) }
    }
    @Test fun rejectsWrongOperandTypes() {
        assertFails { ProgramChecks.validate(program(Statement.Return(Expression.Binary(Operation.ADD,count,Expression.BooleanValue(true))))) }
    }
    @Test fun rejectsLeakingBranchLocals() {
        assertFails { ProgramChecks.validate(program(Statement.Branch(Expression.BooleanValue(true),listOf(Statement.Bind("x",ValueType.U64,false,count)),emptyList()),Statement.Return(Expression.Variable("x")))) }
    }
    @Test fun rejectsUnreachableCodeAndZeroErrors() {
        assertFails { ProgramChecks.validate(program(Statement.Return(count),Statement.Return(count))) }
        assertFails { ProgramChecks.validate(program(Statement.Require(Expression.BooleanValue(false),0u),Statement.Return(count))) }
    }
    @Test fun backendCanBeReusedWithoutLeakingState() {
        val backend=CounterC();val a=program(Statement.Return(Expression.Binary(Operation.ADD,count,Expression.Number(1u))))
        val first=backend.compile(a,"@KORAL_FUNCTION@")
        backend.compile(program(Statement.Return(count)),"@KORAL_FUNCTION@")
        assertEquals(first,backend.compile(a,"@KORAL_FUNCTION@"))
    }
    @Test fun roundTripsSeveralInstructionsWithDifferentArguments() {
        val source=CounterProgram("multi.kt",listOf(
            CounterInstruction("reset",emptyList(),listOf(Statement.Return(Expression.Number(0u)))),
            CounterInstruction("add_scaled",listOf("amount","scale"),listOf(Statement.Return(Expression.Binary(Operation.ADD,count,Expression.Variable("amount")))))
        ))
        ProgramChecks.validate(source)
        val file=Files.createTempFile("koral-multi-",".ir")
        try { ProgramCodec.write(source,file);assertEquals(source,ProgramCodec.read(file)) } finally { Files.delete(file) }
    }
    @Test fun rejectsDuplicateAndReservedInstructionNames() {
        val instruction=CounterInstruction("increment",listOf("by"),listOf(Statement.Return(count)))
        assertFails { ProgramChecks.validate(CounterProgram("bad.kt",listOf(instruction,instruction))) }
        assertFails { ProgramChecks.validate(CounterProgram("bad.kt",listOf(instruction.copy(name="initialize")))) }
    }
    @Test fun instructionArgumentsStayInTheirOwnScope() {
        val first=CounterInstruction("set_count",listOf("value"),listOf(Statement.Return(Expression.Variable("value"))))
        val second=CounterInstruction("reset",emptyList(),listOf(Statement.Return(Expression.Variable("value"))))
        assertFails { ProgramChecks.validate(CounterProgram("bad.kt",listOf(first,second))) }
    }
    @Test fun rejectsRepeatedAndExcessiveArguments() {
        val instruction=CounterInstruction("increment",listOf("by","by"),listOf(Statement.Return(count)))
        assertFails { ProgramChecks.validate(CounterProgram("bad.kt",listOf(instruction))) }
        assertFails { ProgramChecks.validate(CounterProgram("bad.kt",listOf(instruction.copy(arguments=(1..9).map { "arg$it" })))) }
    }

    private val pool=StateLayout("PoolState","pool",listOf("reserve_a","reserve_b"))
    @Test fun typedStateCodecAndFieldReads() {
        val state=Expression.Variable("state")
        val source=CounterProgram("pool.kt",listOf(CounterInstruction("update",listOf("amount"),listOf(Statement.Return(Expression.Construct(listOf(Expression.Field(state,"reserve_a"),Expression.Variable("amount"))))))),pool)
        ProgramChecks.validate(source)
        val file=Files.createTempFile("koral-state-",".ir")
        try { ProgramCodec.write(source,file);assertEquals(source,ProgramCodec.read(file)) } finally { Files.delete(file) }
    }
    @Test fun rejectsUnknownFieldsAndWrongConstructors() {
        fun source(value:Expression)=CounterProgram("bad.kt",listOf(CounterInstruction("update",emptyList(),listOf(Statement.Return(value)))),pool)
        assertFails { ProgramChecks.validate(source(Expression.Construct(listOf(Expression.Number(0u))))) }
        assertFails { ProgramChecks.validate(source(Expression.Construct(listOf(Expression.Field(Expression.Variable("state"),"missing"),Expression.Number(0u))))) }
        assertFails { ProgramChecks.validate(source(Expression.Number(0u))) }
    }
    @Test fun checksLayoutBoundsAndSeed() {
        val source=CounterProgram("bad.kt",listOf(CounterInstruction("update",emptyList(),listOf(Statement.Return(Expression.Variable("state"))))),pool)
        assertFails { ProgramChecks.validate(source.copy(account=pool.copy(seed="a".repeat(33)))) }
        assertFails { ProgramChecks.validate(source.copy(account=pool.copy(fields=emptyList()))) }
        assertFails { ProgramChecks.validate(source.copy(account=pool.copy(fields=listOf("a","a")))) }
    }

    @Test fun externalAccountsAndCpiRoundTrip() {
        val instruction=CounterInstruction("pay",listOf("amount"),listOf(
            Statement.TransferLamports(Expression.Variable("to"),Expression.Variable("amount")),
            Statement.Return(Expression.AccountLamports(Expression.Variable("to")))
        ),listOf(AccountParameter("to",true)))
        val source=CounterProgram("pay.kt",listOf(instruction));ProgramChecks.validate(source)
        val file=Files.createTempFile("koral-cpi-",".ir")
        try { ProgramCodec.write(source,file);assertEquals(source,ProgramCodec.read(file)) } finally { Files.delete(file) }
    }
    @Test fun readonlyAccountCannotBeUsedAsTransferDestination() {
        val instruction=CounterInstruction("pay",emptyList(),listOf(
            Statement.TransferLamports(Expression.Variable("to"),Expression.Number(1u)),Statement.Return(count)
        ),listOf(AccountParameter("to",false)))
        assertFails { ProgramChecks.validate(CounterProgram("bad.kt",listOf(instruction))) }
    }
    @Test fun balanceReadRequiresAnAccount() {
        assertFails { ProgramChecks.validate(program(Statement.Return(Expression.AccountLamports(count)))) }
    }
    @Test fun externalAndDataArgumentsCannotShareNames() {
        val instruction=CounterInstruction("read",listOf("target"),listOf(Statement.Return(count)),listOf(AccountParameter("target",false)))
        assertFails { ProgramChecks.validate(CounterProgram("bad.kt",listOf(instruction))) }
    }

}
